/*
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.microprofile.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.microprofile.concurrent.spi.ConcurrencyProvider;

/**
 * This interface offers various methods for capturing the context of the current thread
 * and applying it to various interfaces that are commonly used with completion stages
 * and executor services.  This allows you to contextualize specific actions that need
 * access to the context of the creator/submitter of the stage/task.
 *
 * <p>Example usage:</p>
 * <pre>
 * <code>&commat;Inject</code> ThreadContext threadContext;
 * ...
 * CompletableFuture&lt;Integer&gt; stage2 = stage1.thenApply(threadContext.contextualFunction(function));
 * ...
 * Future&lt;Integer&gt; future = executor.submit(threadContext.contextualCallable(callable));
 * </pre>
 *
 * <p>This interface is intentionally kept compatible with ContextService,
 * with the hope that its methods might one day be contributed to that specification.</p>
 */
public interface ThreadContext {
    /**
     * Creates a new {@link Builder} instance.
     *
     * @return a new {@link Builder} instance.
     */
    public static Builder builder() {
        return ConcurrencyProvider.instance().getConcurrencyManager().newThreadContextBuilder();
    }

    /**
     * <p>Builder for {@link ThreadContext} instances.</p>
     *
     * <p>Example usage:</p>
     * <pre><code> ThreadContext threadContext = ThreadContext.builder()
     *                                                   .propagated(ThreadContext.APPLICATION, ThreadContext.SECURITY)
     *                                                   .unchanged(ThreadContext.TRANSACTION)
     *                                                   .build();
     * ...
     * </code></pre>
     */
    interface Builder {
        /**
         * <p>Builds a new {@link ThreadContext} instance with the
         * configuration that this builder represents as of the point in time when
         * this method is invoked.</p>
         *
         * <p>After {@link #build} is invoked, the builder instance retains its
         * configuration and may be further updated to represent different
         * configurations and build additional <code>ThreadContext</code>
         * instances.</p>
         *
         * <p>All created instances of {@link ThreadContext} are destroyed
         * when the application is stopped. The container automatically shuts down these
         * {@link ThreadContext} instances, cancels their remaining
         * <code>CompletableFuture</code>s and <code>CompletionStage</code>s, and
         * and raises <code>IllegalStateException</code> to reject subsequent attempts
         * to apply previously captured thread context.</p>
         *
         * @return new instance of {@link ThreadContext}.
         * @throws IllegalStateException for any of the following error conditions
         *         <ul>
         *         <li>if one or more of the same context types appear in multiple
         *         of the following sets:
         *         ({@link #cleared}, {@link #propagated}, {@link #unchanged})</li>
         *         <li>if a thread context type that is configured to be
         *         {@link #cleared} or {@link #propagated} is unavailable</li>
         *         <li>if more than one <code>ThreadContextProvider</code> has the
         *         same thread context
         *         {@link org.eclipse.microprofile.concurrent.spi.ThreadContextProvider#getThreadContextType type}
         *         </li>
         *         </ul>
         */
        ThreadContext build();

        /**
         * <p>Defines the set of thread context types to clear from the thread
         * where the action or task executes. The previous context is resumed
         * on the thread after the action or task ends.</p>
         *
         * <p>This set replaces the <code>cleared</code> set that was
         * previously specified on the builder instance, if any.</p>
         *
         * <p>The default set of cleared thread context types is
         * {@link ThreadContext#TRANSACTION}, which means that a transaction
         * is not active on the thread when the action or task runs, such
         * that each action or task is able to independently start and end
         * its own transactional work.</p>
         *
         * <p>Constants for specifying some of the core context types are provided
         * on {@link ThreadContext}. Other thread context types must be defined
         * by the specification that defines the context type or by a related
         * MicroProfile specification.</p>
         *
         * @param types types of thread context to clear from threads that run
         *        actions and tasks.
         * @return the same builder instance upon which this method is invoked.
         */
        Builder cleared(String... types);

        /**
         * <p>Defines the set of thread context types to capture from the thread
         * that contextualizes an action or task. This context is later
         * re-established on the thread(s) where the action or task executes.</p>
         *
         * <p>This set replaces the <code>propagated</code> set that was
         * previously specified on the builder instance, if any.</p>
         *
         * <p>The default set of propagated thread context types is
         * {@link ThreadContext#ALL_REMAINING}, which includes all available
         * thread context types that support capture and propagation to other
         * threads, except for those that are explicitly {@link cleared},
         * which, by default is {@link ThreadContext#TRANSACTION} context,
         * in which case is suspended from the thread that runs the action or
         * task.</p>
         *
         * <p>Constants for specifying some of the core context types are provided
         * on {@link ThreadContext}. Other thread context types must be defined
         * by the specification that defines the context type or by a related
         * MicroProfile specification.</p>
         *
         * <p>Thread context types which are not otherwise included in this set or
         * in the {@link #unchanged} set are cleared from the thread of execution
         * for the duration of the action or task.</p>
         *
         * <p>A <code>ThreadContext</code> must fail to {@link #build} if the same
         * context type is included in this set as well as in the {@link #unchanged}
         * set.</p>
         *
         * @param types types of thread context to capture and propagated.
         * @return the same builder instance upon which this method is invoked.
         */
        Builder propagated(String... types);

        /**
         * <p>Defines a set of thread context types that are essentially ignored,
         * in that they are neither captured nor are they propagated or cleared
         * from thread(s) that execute the action or task.</p>
         *
         * <p>This set replaces the <code>unchanged</code> set that was previously
         * specified on the builder instance.</p>
         *
         * <p>Constants for specifying some of the core context types are provided
         * on {@link ThreadContext}. Other thread context types must be defined
         * by the specification that defines the context type or by a related
         * MicroProfile specification.</p>
         *
         * <p>The configuration of <code>unchanged</code> context is provided for
         * advanced patterns where it is desirable to leave certain context types
         * on the executing thread.</p>
         *
         * <p>For example, to run under the transaction of the thread of execution,
         * with security context cleared and all other thread contexts propagated:</p>
         * <pre><code> ThreadContext threadContext = ThreadContext.builder()
         *                                                   .unchanged(ThreadContext.TRANSACTION)
         *                                                   .cleared(ThreadContext.SECURITY)
         *                                                   .propagated(ThreadContext.ALL_REMAINING)
         *                                                   .build();
         * ...
         * task = threadContext.contextualRunnable(new MyTransactionlTask());
         * ...
         * // on another thread,
         * tx.begin();
         * ...
         * task.run(); // runs under the transaction due to 'unchanged'
         * tx.commit();
         * </code></pre>
         *
         * <p>A {@link ThreadContext} must fail to {@link #build} if the same
         * context type is included in this set as well as in the set specified by
         * {@link #propagated}.</p>
         *
         * @param types types of thread context to leave unchanged on the thread.
         * @return the same builder instance upon which this method is invoked.
         */
        Builder unchanged(String... types);
    }

    /**
     * <p>Identifier for all available thread context types which are
     * not specified individually under <code>cleared</code>,
     * <code>propagated</code>, or <code>unchanged</code>.</p>
     *
     * <p>When using this constant, be aware that bringing in a new
     * context provider or updating levels of an existing context provider
     * might change the set of available thread context types.</p>
     *
     * @see ManagedExecutor.Builder#cleared
     * @see ManagedExecutor.Builder#propagated
     * @see ManagedExecutorConfig#cleared
     * @see ManagedExecutorConfig#propagated
     * @see ThreadContext.Builder
     * @see ThreadContextConfig
     */
    static final String ALL_REMAINING = "Remaining";

    /**
     * Identifier for application context. Application context controls the
     * application component that is associated with a thread. It can determine
     * the thread context class loader as well as the set of resource references
     * that are available for lookup or resource injection. An empty/default
     * application context means that the thread is not associated with any
     * application.
     *
     * @see ManagedExecutor.Builder#cleared
     * @see ManagedExecutor.Builder#propagated
     * @see ManagedExecutorConfig#cleared
     * @see ManagedExecutorConfig#propagated
     * @see ThreadContext.Builder
     * @see ThreadContextConfig
     */
    static final String APPLICATION = "Application";

    /**
     * Identifier for CDI context. CDI context controls the availability of CDI
     * scopes. An empty/default CDI context means that the thread does not have
     * access to the scope of the session, request, and so forth that created the
     * contextualized action.
     *
     * @see ManagedExecutor.Builder#cleared
     * @see ManagedExecutor.Builder#propagated
     * @see ManagedExecutorConfig#cleared
     * @see ManagedExecutorConfig#propagated
     * @see ThreadContext.Builder
     * @see ThreadContextConfig
     */
    static final String CDI = "CDI";

    /**
     * Identifier for security context. Security context controls the credentials
     * that are associated with the thread. An empty/default security context
     * means that the thread is unauthenticated.
     * 
     * @see ManagedExecutor.Builder#cleared
     * @see ManagedExecutor.Builder#propagated
     * @see ManagedExecutorConfig#cleared
     * @see ManagedExecutorConfig#propagated
     * @see ThreadContext.Builder
     * @see ThreadContextConfig
     */
    static final String SECURITY = "Security";

    /**
     * Identifier for transaction context. Transaction context controls the
     * active transaction scope that is associated with the thread.
     * Implementations are not expected to propagate transaction context across
     * threads. Instead, the concept of transaction context is provided for its
     * cleared context, which means the active transaction on the thread
     * is suspended such that a new transaction can be started if so desired.
     * In most cases, the most desirable behavior will be to leave transaction
     * context defaulted to cleared (suspended),
     * in order to prevent dependent actions and tasks from accidentally
     * enlisting in transactions that are on the threads where they happen to
     * run.
     *
     * @see ManagedExecutor.Builder#cleared
     * @see ManagedExecutor.Builder#propagated
     * @see ManagedExecutorConfig#cleared
     * @see ManagedExecutorConfig#propagated
     * @see ThreadContext.Builder
     * @see ThreadContextConfig
     */
    static final String TRANSACTION = "Transaction";

    /**
     * <p>Creates an <code>Executor</code>that runs tasks on the same thread from which
     * <code>execute</code>is invoked but with context that is captured from the thread
     * that invokes <code>currentContextExecutor</code>.</p>
     *
     * <p>Example usage:</p>
     * <pre>
     * <code>Executor contextSnapshot = threadContext.currentContextExecutor();
     * ...
     * // from another thread, or after thread context has changed,
     * contextSnapshot.execute(() -> obj.doSomethingThatNeedsContext());
     * contextSnapshot.execute(() -> doSomethingElseThatNeedsContext(x, y));
     * </code></pre>
     *
     * @return an executor that wraps the <code>execute</code> method with context.
     */
    Executor currentContextExecutor();

    /**
     * <p>Wraps a <code>Callable</code> with context that is captured from the thread that invokes
     * <code>contextualCallable</code>.</p>
     * 
     * <p>When <code>call</code> is invoked on the proxy instance,
     * context is first established on the thread that will run the <code>call</code> method,
     * then the <code>call</code> method of the provided <code>Callable</code> is invoked.
     * Finally, the previous context is restored on the thread, and the result of the
     * <code>Callable</code> is returned to the invoker.</p> 
     *
     * @param <R> callable result type.
     * @param callable instance to contextualize.
     * @return contextualized proxy instance that wraps execution of the <code>call</code> method with context.
     */
    <R> Callable<R> contextualCallable(Callable<R> callable);

    /**
     * <p>Wraps a <code>BiConsumer</code> with context that is captured from the thread that invokes
     * <code>contextualConsumer</code>.</p>
     *
     * <p>When <code>accept</code> is invoked on the proxy instance,
     * context is first established on the thread that will run the <code>accept</code> method,
     * then the <code>accept</code> method of the provided <code>BiConsumer</code> is invoked.
     * Finally, the previous context is restored on the thread, and control is returned to the invoker.</p>
     *
     * @param <T> type of first parameter to consumer.
     * @param <U> type of second parameter to consumer.
     * @param consumer instance to contextualize.
     * @return contextualized proxy instance that wraps execution of the <code>accept</code> method with context.
     */
    <T, U> BiConsumer<T, U> contextualConsumer(BiConsumer<T, U> consumer);

    /**
     * <p>Wraps a <code>Consumer</code> with context that is captured from the thread that invokes
     * <code>contextualConsumer</code>.</p>
     * 
     * <p>When <code>accept</code> is invoked on the proxy instance,
     * context is first established on the thread that will run the <code>accept</code> method,
     * then the <code>accept</code> method of the provided <code>Consumer</code> is invoked.
     * Finally, the previous context is restored on the thread, and control is returned to the invoker.</p> 
     *
     * @param <T> type of parameter to consumer.
     * @param consumer instance to contextualize.
     * @return contextualized proxy instance that wraps execution of the <code>accept</code> method with context.
     */
    <T> Consumer<T> contextualConsumer(Consumer<T> consumer);

    /**
     * <p>Wraps a <code>BiFunction</code> with context that is captured from the thread that invokes
     * <code>contextualFunction</code>.</p>
     *
     * <p>When <code>apply</code> is invoked on the proxy instance,
     * context is first established on the thread that will run the <code>apply</code> method,
     * then the <code>apply</code> method of the provided <code>BiFunction</code> is invoked.
     * Finally, the previous context is restored on the thread, and the result of the
     * <code>BiFunction</code> is returned to the invoker.</p>
     *
     * @param <T> type of first parameter to function.
     * @param <U> type of second parameter to function.
     * @param <R> function result type.
     * @param function instance to contextualize.
     * @return contextualized proxy instance that wraps execution of the <code>apply</code> method with context.
     */
    <T, U, R> BiFunction<T, U, R> contextualFunction(BiFunction<T, U, R> function);

    /**
     * <p>Wraps a <code>Function</code> with context that is captured from the thread that invokes
     * <code>contextualFunction</code>.</p>
     * 
     * <p>When <code>apply</code> is invoked on the proxy instance,
     * context is first established on the thread that will run the <code>apply</code> method,
     * then the <code>apply</code> method of the provided <code>Function</code> is invoked.
     * Finally, the previous context is restored on the thread, and the result of the
     * <code>Function</code> is returned to the invoker.</p> 
     *
     * @param <T> type of parameter to function.
     * @param <R> function result type.
     * @param function instance to contextualize.
     * @return contextualized proxy instance that wraps execution of the <code>apply</code> method with context.
     */
    <T, R> Function<T, R> contextualFunction(Function<T, R> function);

    /**
     * <p>Wraps a <code>Runnable</code> with context that is captured from the thread that invokes
     * <code>ContextualRunnable</code>.</p>
     * 
     * <p>When <code>run</code> is invoked on the proxy instance,
     * context is first established on the thread that will run the <code>run</code> method,
     * then the <code>run</code> method of the provided <code>Runnable</code> is invoked.
     * Finally, the previous context is restored on the thread, and control is returned to the invoker.</p> 
     * 
     * @param runnable instance to contextualize.
     * @return contextualized proxy instance that wraps execution of the <code>run</code> method with context.
     */
    Runnable contextualRunnable(Runnable runnable);

    /**
     * <p>Wraps a <code>Supplier</code> with context captured from the thread that invokes
     * <code>contextualSupplier</code>.</p>
     * 
     * <p>When <code>supply</code> is invoked on the proxy instance,
     * context is first established on the thread that will run the <code>supply</code> method,
     * then the <code>supply</code> method of the provided <code>Supplier</code> is invoked.
     * Finally, the previous context is restored on the thread, and the result of the
     * <code>Supplier</code> is returned to the invoker.</p> 
     *
     * @param <R> supplier result type.
     * @param supplier instance to contextualize.
     * @return contextualized proxy instance that wraps execution of the <code>supply</code> method with context.
     */
    <R> Supplier<R> contextualSupplier(Supplier<R> supplier);

    /**
     * <p>Returns a new <code>CompletableFuture</code> that is completed by the completion of the
     * specified stage.</p>
     *
     * <p>The container supplies the default asynchronous execution facility for the new completable
     * future that is returned by this method and all dependent stages that are created from it,
     * and all dependent stages that are created from those, and so forth.</p>
     *
     * <p>When dependent stages are created from the new completable future, thread context is captured
     * from the thread that creates the dependent stage and is applied to the thread that runs the
     * action, being removed afterward. When dependent stages are created from these dependent stages,
     * and likewise from any dependent stages created from those, and so on, thread context is captured
     * from the respective thread that creates each dependent stage. This guarantees that the action
     * performed by each stage always runs under the thread context of the code that creates the stage.
     * </p>
     *
     * <p>Invocation of this method does not impact thread context propagation for the supplied
     * completable future or any dependent stages created from it, other than the new dependent
     * completable future that is created by this method.</p>
     *
     * @param stage a completable future whose completion triggers completion of the new completable
     *        future that is created by this method.
     * @return the new completable future.
     */
    <T> CompletableFuture<T> withContextCapture(CompletableFuture<T> stage);

    /**
     * <p>Returns a new <code>CompletionStage</code> that is completed by the completion of the
     * specified stage.</p>
     *
     * <p>The container supplies the default asynchronous execution facility for the new completion
     * stage that is returned by this method and all dependent stages that are created from it,
     * and all dependent stages that are created from those, and so forth.</p>
     *
     * <p>When dependent stages are created from the new completion stage, thread context is captured
     * from the thread that creates the dependent stage and is applied to the thread that runs the
     * action, being removed afterward. When dependent stages are created from these dependent stages,
     * and likewise from any dependent stages created from those, and so on, thread context is captured
     * from the respective thread that creates each dependent stage. This guarantees that the action
     * performed by each stage always runs under the thread context of the code that creates the stage.
     * </p>
     *
     * <p>Invocation of this method does not impact thread context propagation for the supplied
     * stage or any dependent stages created from it, other than the new dependent
     * completion stage that is created by this method.</p>
     *
     * @param stage a completion stage whose completion triggers completion of the new stage
     *        that is created by this method.
     * @return the new completion stage.
     */
    <T> CompletionStage<T> withContextCapture(CompletionStage<T> stage);
}