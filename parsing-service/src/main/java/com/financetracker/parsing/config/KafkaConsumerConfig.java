//package com.financetracker.parsing.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.task.VirtualThreadTaskExecutor;
//import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
//import org.springframework.kafka.listener.ContainerCustomizer;
//
///**
// * Configures the Kafka listener container to hand off message processing
// * to a virtual-thread-per-task executor ({@link VirtualThreadTaskExecutor},
// * available since Spring Framework 6.1) rather than Spring Kafka's
// * default fixed platform-thread pool.
// *
// * <p>Why this matters here specifically: processing one
// * {@code statement.ingested} message means making several LLM HTTP calls
// * (one per chunk, plus one embedding call per extracted transaction) -
// * i.e. the listener thread spends most of its time blocked on network
// * I/O, not doing CPU work. A virtual thread parks cheaply during that
// * wait instead of pinning a limited platform thread, so this one Kafka
// * consumer can have many statement-processing runs "in flight"
// * (overlapping their I/O waits) without needing a large, manually-tuned
// * platform thread pool.
// */
//@Configuration
//public class KafkaConsumerConfig {
//
//    @Bean
//    public ContainerCustomizer<String, String, ConcurrentMessageListenerContainer<String, String>>
//    virtualThreadContainerCustomizer() {
//        return container -> container.getContainerProperties()
//                .setListenerTaskExecutor(new VirtualThreadTaskExecutor("parsing-kafka-listener-"));
//    }
//}
