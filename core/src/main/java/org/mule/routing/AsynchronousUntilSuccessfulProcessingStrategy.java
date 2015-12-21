/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.routing;

import static org.mule.routing.UntilSuccessful.DEFAULT_PROCESS_ATTEMPT_COUNT_PROPERTY_VALUE;
import static org.mule.routing.UntilSuccessful.PROCESS_ATTEMPT_COUNT_PROPERTY_NAME;
import org.mule.DefaultMuleEvent;
import org.mule.DefaultMuleMessage;
import org.mule.VoidMuleEvent;
import org.mule.api.MessagingException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.lifecycle.Startable;
import org.mule.api.lifecycle.Stoppable;
import org.mule.api.store.ObjectStoreException;
import org.mule.config.i18n.CoreMessages;
import org.mule.config.i18n.MessageFactory;
import org.mule.retry.RetryPolicyExhaustedException;
import org.mule.util.concurrent.ThreadNameHelper;
import org.mule.util.queue.objectstore.QueueKey;
import org.mule.util.store.QueuePersistenceObjectStore;

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Until successful asynchronous processing strategy.
 * <p/>
 * It will return successfully to the flow executing the router once it was able to
 * store the message in the object store.
 * <p/>
 * After that it will asynchronously try to process the message through the internal route.
 * If route was not successfully executed after the configured retry count then the message
 * will be routed to the defined dead letter queue route or in case there is no dead letter
 * queue route then it will be handled by the flow exception strategy.
 */
public class AsynchronousUntilSuccessfulProcessingStrategy extends AbstractUntilSuccessfulProcessingStrategy implements Initialisable, Startable, Stoppable
{

    private static final Random random = new Random();
    protected transient Log logger = LogFactory.getLog(getClass());
    private ExecutorService pool;
    private ScheduledExecutorService scheduledRetriesPool;

    @Override
    public void initialise() throws InitialisationException
    {
        if (getUntilSuccessfulConfiguration().getObjectStore() == null)
        {
            throw new InitialisationException(
                    MessageFactory.createStaticMessage("A ListableObjectStore must be configured on UntilSuccessful."),
                    this);
        }
    }

    @Override
    public void start()
    {
        final String threadPrefix = String.format("%s%s.%s", ThreadNameHelper.getPrefix(getUntilSuccessfulConfiguration().getMuleContext()),
                                                  getUntilSuccessfulConfiguration().getFlowConstruct().getName(), "until-successful");
        pool = getUntilSuccessfulConfiguration().getThreadingProfile().createPool(threadPrefix);
        scheduledRetriesPool = getUntilSuccessfulConfiguration().createScheduledRetriesPool(threadPrefix);

        scheduleAllPendingEventsForProcessing();
    }

    @Override
    public void stop()
    {
        scheduledRetriesPool.shutdown();
        scheduledRetriesPool = null;
        pool.shutdown();
        pool = null;
    }

    @Override
    protected MuleEvent doRoute(MuleEvent event) throws MessagingException
    {
        try
        {
            final Serializable eventStoreKey = storeEvent(event);
            scheduleForProcessing(eventStoreKey, true);
            if (getUntilSuccessfulConfiguration().getAckExpression() == null)
            {
                return VoidMuleEvent.getInstance();
            }
            return processResponseThroughAckResponseExpression(event);
        }
        catch (final Exception e)
        {
            throw new MessagingException(
                    MessageFactory.createStaticMessage("Failed to schedule the event for processing"), event, e,
                    getUntilSuccessfulConfiguration().getRouter());
        }
    }

    private void scheduleAllPendingEventsForProcessing()
    {
        try
        {
            for (final Serializable eventStoreKey : getUntilSuccessfulConfiguration().getObjectStore().allKeys())
            {
                try
                {
                    scheduleForProcessing(eventStoreKey, true);
                }
                catch (final Exception e)
                {
                    logger.error(
                            MessageFactory.createStaticMessage("Failed to schedule for processing event stored with key: "
                                                               + eventStoreKey), e);
                }
            }
        }
        catch (Exception e)
        {
            logger.warn("Failure during scheduling of until successful previous jobs " + e.getMessage());
            if (logger.isDebugEnabled())
            {
                logger.debug(e);
            }
        }
    }

    private void scheduleForProcessing(final Serializable eventStoreKey, boolean firstTime)
    {
        if (firstTime)
        {
            submitForProcessing(eventStoreKey);
        }
        else
        {
            this.scheduledRetriesPool.schedule(new Callable<Object>()
            {
                @Override
                public Object call() throws Exception
                {
                    submitForProcessing(eventStoreKey);
                    return null;
                }
            }, getUntilSuccessfulConfiguration().getMillisBetweenRetries(), TimeUnit.MILLISECONDS);
        }
    }

    protected void submitForProcessing(final Serializable eventStoreKey)
    {
        this.pool.execute(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    retrieveAndProcessEvent(eventStoreKey);
                }
                catch (Exception e)
                {
                    incrementProcessAttemptCountAndRescheduleOrRemoveFromStore(eventStoreKey);
                }
            }
        });
    }

    private void incrementProcessAttemptCountAndRescheduleOrRemoveFromStore(final Serializable eventStoreKey)
    {
        try
        {
            final MuleEvent event = getUntilSuccessfulConfiguration().getObjectStore().remove(eventStoreKey);
            final MuleEvent mutableEvent = threadSafeCopy(event);

            final MuleMessage message = mutableEvent.getMessage();
            final Integer deliveryAttemptCount = message.getInvocationProperty(
                    PROCESS_ATTEMPT_COUNT_PROPERTY_NAME, DEFAULT_PROCESS_ATTEMPT_COUNT_PROPERTY_VALUE);

            if (deliveryAttemptCount <= getUntilSuccessfulConfiguration().getMaxRetries())
            {
                // we store the incremented version unless the max attempt count has
                // been reached
                message.setInvocationProperty(PROCESS_ATTEMPT_COUNT_PROPERTY_NAME, deliveryAttemptCount + 1);
                getUntilSuccessfulConfiguration().getObjectStore().store(eventStoreKey, mutableEvent);
                this.scheduleForProcessing(eventStoreKey, false);
            }
            else
            {
                abandonRetries(event, mutableEvent);
            }
        }
        catch (final ObjectStoreException ose)
        {
            logger.error("Failed to increment failure count for event stored with key: " + eventStoreKey, ose);
        }
    }

    private Serializable storeEvent(final MuleEvent event) throws ObjectStoreException
    {
        final MuleMessage message = event.getMessage();
        final Integer deliveryAttemptCount = message.getInvocationProperty(
                PROCESS_ATTEMPT_COUNT_PROPERTY_NAME, DEFAULT_PROCESS_ATTEMPT_COUNT_PROPERTY_VALUE);
        return storeEvent(event, deliveryAttemptCount);
    }

    private Serializable storeEvent(final MuleEvent event, final int deliveryAttemptCount)
            throws ObjectStoreException
    {
        final MuleMessage message = event.getMessage();
        message.setInvocationProperty(PROCESS_ATTEMPT_COUNT_PROPERTY_NAME, deliveryAttemptCount);
        final Serializable eventStoreKey = buildQueueKey(event);
        getUntilSuccessfulConfiguration().getObjectStore().store(eventStoreKey, event);
        return eventStoreKey;
    }

    public static Serializable buildQueueKey(final MuleEvent muleEvent)
    {
        // the key is built in way to prevent UntilSuccessful workers across a
        // cluster to compete for the same
        // events over a shared object store
        // it also adds a random trailer to support events which have been
        // splitted and thus have the same id. Random number was chosen over
        // UUID for performance reasons
        String key = String.format("%s-%s-%s-%d", muleEvent.getFlowConstruct(), muleEvent.getMuleContext().getClusterId(), muleEvent.getId(), random.nextInt());

        return new QueueKey(QueuePersistenceObjectStore.DEFAULT_QUEUE_STORE, key);
    }

    private void abandonRetries(final MuleEvent event, final MuleEvent mutableEvent)
    {
        if (getUntilSuccessfulConfiguration().getDlqMP() == null)
        {
            logger.info("Retry attempts exhausted and no DLQ defined");
            RetryPolicyExhaustedException retryPolicyExhaustedException = new RetryPolicyExhaustedException(
                    CoreMessages.createStaticMessage("until-successful retries exhausted"), this);
            event.getFlowConstruct()
                    .getExceptionListener()
                    .handleException(new MessagingException(event, retryPolicyExhaustedException), mutableEvent);
            return;
        }
        //we need another local copy in case mutableEvent is modified in the DLQ
        MuleEvent eventCopy = threadSafeCopy(event);
        logger.info("Retry attempts exhausted, routing message to DLQ: " + getUntilSuccessfulConfiguration().getDlqMP());
        try
        {
            getUntilSuccessfulConfiguration().getDlqMP().process(mutableEvent);
        }
        catch (MessagingException e)
        {
            event.getFlowConstruct().getExceptionListener().handleException(e, eventCopy);
        }
        catch (Exception e)
        {
            event.getFlowConstruct()
                    .getExceptionListener()
                    .handleException(new MessagingException(event, e), eventCopy);
        }
    }

    private void removeFromStore(final Serializable eventStoreKey)
    {
        try
        {
            getUntilSuccessfulConfiguration().getObjectStore().remove(eventStoreKey);
        }
        catch (final ObjectStoreException ose)
        {
            logger.warn("Failed to remove following event from store with key: " + eventStoreKey);
        }
    }

    private void retrieveAndProcessEvent(final Serializable eventStoreKey) throws ObjectStoreException
    {
        final MuleEvent persistedEvent = getUntilSuccessfulConfiguration().getObjectStore().retrieve(eventStoreKey);
        final MuleEvent mutableEvent = threadSafeCopy(persistedEvent);
        processEvent(mutableEvent);
        removeFromStore(eventStoreKey);
    }

    protected MuleEvent threadSafeCopy(final MuleEvent event)
    {
        final DefaultMuleMessage message = new DefaultMuleMessage(event.getMessage().getPayload(),
                                                                  event.getMessage(), getUntilSuccessfulConfiguration().getMuleContext());

        return new DefaultMuleEvent(message, event);
    }

}
