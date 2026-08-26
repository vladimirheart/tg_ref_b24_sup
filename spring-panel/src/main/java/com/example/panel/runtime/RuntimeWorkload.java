package com.example.panel.runtime;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(RuntimeWorkloadCondition.class)
public @interface RuntimeWorkload {
    String id();

    RuntimeRole[] roles();

    RuntimeReplicaPolicy replicaPolicy();
}
