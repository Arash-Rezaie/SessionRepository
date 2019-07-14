package com.arash.sessionrepository;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = ElementType.METHOD)
@Retention(value = RetentionPolicy.RUNTIME)
@Inherited
public @interface Subscribe {
    /**
     * @return the entry name you are interest in
     */
    String keyword();

    /**
     * @return true if you are willing to run your listener ins main thread
     */
    boolean mainThread() default true;

    /**
     * @return the key will be deleted from session as soon as possible
     */
    boolean deleteKey() default true;
}
