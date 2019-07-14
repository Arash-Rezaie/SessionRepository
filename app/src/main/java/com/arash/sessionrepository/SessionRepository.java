package com.arash.sessionrepository;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author Arash Rezaie
 * @version 1
 * <p>
 * This class is a simple repository so you can keep your data in this class.<br>
 * Also you may pass an event through different activities. The class supports Observable pattern, so you can subscribe for an event and get notified when ever an event is produced.
 * </p>
 * <p>
 * This class is a repository; so it is responsible for holding data as long as the session is alive. You can remove a key-value pair by calling remove(). Also, observer methods with "deleteKey" attribute have the same effect.
 * In this case, what happens when an observer deletes a key and some other module needs to access the key yet?<br>
 * The answer is: All consequences is up to you. You are the manager.
 * </p>
 */
public final class SessionRepository {
    // session repository
    private final static HashMap<String, Session> repository = new HashMap<>();

    // default session name
    private final static String DEFAULT_SESSION_NAME = Session.class.getName() + "_default";

    // I do not want to keep classes waiting in registration time, so I hired a thread pool for all sessions
    private final static Executor worker = Executors.newSingleThreadExecutor();

    // client may prefer to get notified in the main thread. This one, helps it out
    private final static Handler handler;

    static {
        // I need to hook to main thread, so let's break it when I can't initialize my handler
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new RuntimeException("Please initiate SessionRepository in main thread");
        }
        handler = new Handler();
    }

    /**
     * Client data is going to be handled in this class
     */
    public static final class Session {
        //session keeps its own name to make its management easier by SessionRepository
        private final String myName;

        //main data repository<key, data>
        private final HashMap<String, Object> session = new HashMap<>();

        //subscriber container<data key, subscribers>
        private HashMap<String, List<Subscriber>> subscribers;

        /**
         * Subscriber info
         */
        private class Subscriber {
            // listener class
            private Object target;

            // listener itself
            private Method method;

            // invoke method in main thread if this field is true
            private boolean mainThread;

            // clear target record if this field is set true
            private boolean deleteFromSession;

            // method parameter's type
            private Class<?> paramType;

            Subscriber(Object target, Method method, boolean mainThread, boolean deleteFromSession) {
                this.target = target;
                this.method = method;
                this.mainThread = mainThread;
                this.deleteFromSession = deleteFromSession;
                this.method.setAccessible(true);
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 0) {
                    this.paramType = Void.class;
                } else if (types.length == 1) {
                    this.paramType = types[0];
                } else {
                    throw new RuntimeException("number of parameters for " + method.getName() + " can not be greater than 1");
                }
            }

            private void run(String key, Object data) {
                if (mainThread) {
                    invoke(key, data);
                } else {
                    worker.execute(() -> invoke(key, data));
                }
            }

            private void invoke(String key, Object data) {
                try {
                    if (paramType == Void.class) {
                        method.invoke(target);
                    } else {
                        method.invoke(target, paramType.cast(data));
                    }
                    if (deleteFromSession) {
                        remove(key);
                    }
                } catch (ClassCastException e) {
                    if (BuildConfig.DEBUG) {
                        System.out.printf("posted data must be of type: " + this.paramType.getName());
                        e.printStackTrace();
                    }
                } catch (IllegalAccessException e) {
                    if (BuildConfig.DEBUG) {
                        System.out.println("Oops! I couldn't invoke " + this.method.getName());
                        e.printStackTrace();
                    }
                } catch (InvocationTargetException e) {
                    if (BuildConfig.DEBUG) {
                        System.out.println("Oops! I couldn't invoke " + this.method.getName());
                        e.printStackTrace();
                    }
                }
            }
        }

        /**
         * Session constructor must be private, as SessionRepository is the only one which is due to create and remove session
         *
         * @param name name of the current session
         */
        private Session(String name) {
            myName = name;
        }

        /**
         * @return number of records in session
         */
        public int getSize() {
            return session.size();
        }

        /**
         * Check if desired key exists
         *
         * @param key the key string
         * @return true if there is a record named as key
         */
        public boolean containsKey(String key) {
            return session.containsKey(key);
        }

        /**
         * Check if desired value exists
         *
         * @param value target value. You must pass in exact object as it is going to be checked by its hashcode
         * @return true if there is at lease one record containing your desired object
         */
        public boolean containsValue(Object value) {
            return session.containsValue(value);
        }

        /**
         * Get desired object
         *
         * @param key          the keyword
         * @param defaultValue when no entry has defined yet, defaultValue would be returned
         * @return desired object
         */
        public Object get(String key, Object defaultValue) {
            if (session.containsKey(key)) {
                return session.get(key);
            } else {
                session.put(key, defaultValue);
                return defaultValue;
            }
        }

        /**
         * Put a pair of key-value into the session. It remains in the session till you remove it or nonsticky subscriber gets called for this key
         *
         * @param key   any name you prefer
         * @param value target object
         */
        public void put(String key, Object value) {
            session.put(key, value);
            if (subscribers != null && subscribers.containsKey(key))
                checkSignal(key);
        }

        /**
         * Remove all key-value pairs from the session, but session remains in the memory
         */
        public void empty() {
            session.clear();
        }

        /**
         * Remove a key from session
         *
         * @param key
         */
        public void remove(String key) {
            session.remove(key);
        }

        /**
         * By calling this method, all marked methods by Subscribe annotation, will be collected into subscriber list.
         * <p>
         * Usually, target class is an activity which contains lots of public methods.
         * To increase the performance, I decided to look for annotated methods, only in the current class.
         * <br>
         * So you are not forced to define your listeners "public" but you must notice that <strong>listeners in parent class doesn't take in</strong>.
         * <br>
         * To do so, you must use the other method.
         * </p>
         *
         * @param listenerClass the object which contains listener methods. Usually it is "this"
         */
        public synchronized void register(Object listenerClass) {
            register(listenerClass, listenerClass.getClass());
        }

        /**
         * By calling this method, all marked methods by Subscribe annotation, will be collected into subscriber list
         *
         * @param listenerClass  the object which contains listener methods. Usually it is "this"
         * @param hierarchyLevel the number of super classes which must be considered
         * @see com.arash.sessionrepository.SessionRepository.Session#register(Object)
         */
        public synchronized void register(Object listenerClass, int hierarchyLevel) {
            Class<?> type = listenerClass.getClass();
            for (int i = 0; i <= hierarchyLevel; i++) {
                register(listenerClass, type);
                type = type.getSuperclass();
                if (type == Object.class)
                    break;
            }
        }

        private void register(Object listenerClass, Class<?> listenerType) {
            if (subscribers == null) {
                subscribers = new HashMap<>();
            }
            //if may be a heavy job to find all subscribers and call them indeed, so let's run it in background thread
            worker.execute(() -> {
                // catch all methods
                final Method[] methods = listenerType.getDeclaredMethods();
                for (int i = methods.length - 1; i >= 0; i--) {
                    // search for Subscribe annotation
                    if (methods[i].isAnnotationPresent(Subscribe.class)) {
                        Subscribe subscribe = methods[i].getAnnotation(Subscribe.class);

                        //extract if features
                        Subscriber subInfo = new Subscriber(listenerClass, methods[i], subscribe.mainThread(), subscribe.deleteKey());

                        //insert method in the list
                        List<Subscriber> lst = subscribers.get(subscribe.keyword());
                        if (lst == null) {
                            lst = new ArrayList<>(3);
                            subscribers.put(subscribe.keyword(), lst);
                        } else {
                            //remove old one
                            for (int j = lst.size() - 1; j >= 0; j--) {
                                if (lst.get(j).method == methods[i]) {
                                    lst.remove(j);
                                    break;
                                }
                            }
                        }

                        lst.add(subInfo);

                        //if data is available invoke the method
                        if (session.containsKey(subscribe.keyword()))
                            checkSignal(subscribe.keyword());
                    }
                }
            });
        }

        /**
         * You must call this method to exclude your class from subscribers list
         *
         * @param listenerClass the class which contains @Subscribe annotation
         */
        public synchronized void unregister(Object listenerClass) {
            if (subscribers != null) {
                //remove all subscribed listener methods from the list
                Iterator<String> itr = subscribers.keySet().iterator();
                while (itr.hasNext()) {
                    List<Subscriber> lst = subscribers.get(itr.next());
                    for (int i = lst.size() - 1; i >= 0; i--) {
                        if (lst.get(i).target == listenerClass)
                            lst.remove(i);
                    }
                }
            }
        }

        /**
         * notify listeners
         *
         * @param keyword entry name
         */
        private synchronized void checkSignal(String keyword) {
            if (subscribers != null && subscribers.size() > 0) {
                List<Subscriber> subscriberList = subscribers.get(keyword);
                if (subscriberList != null) {
                    for (Subscriber subscriber : subscriberList) {
                        handler.post(() -> subscriber.run(keyword, session.get(keyword)));
                    }
                }
            }
        }
    }

    private SessionRepository() {
    }

    /**
     * There is a session provided by default. You may use it or create new session by calling getSession() method.
     * <p>
     * <strong>Session name is unique.</strong>
     * </p>
     *
     * @return a session instance
     */
    public synchronized static Session getDefaultSession() {
        return getSession(DEFAULT_SESSION_NAME);
    }

    /**
     * Catch a session by via this method. This class follows multitone design pattern, so you can not use a session name twice
     * <p>
     * <strong>Session name is unique.</strong>
     * </p>
     *
     * @param sessionName any name you prefer
     * @return a session instance
     */
    public synchronized static Session getSession(String sessionName) {
        Session session = repository.get(sessionName);
        if (session == null) {
            session = new Session(sessionName);
            repository.put(sessionName, session);
        }
        return session;
    }

    /**
     * Remove a session by calling this method. When a session removes, all its data will be removed and subscribed methods can not get invoked any more.
     * <br>
     * It is true about all sessions even defaultSession.
     * <p>
     * If you wish to remove default session, Please notice that other classes may use this session too and removing this session breaks their connection.
     * </p>
     *
     * @param sessionName a unique name for session
     */
    public synchronized static void removeSession(String sessionName) {
        repository.remove(sessionName);
    }

    /**
     * @param session target session
     * @see com.arash.sessionrepository.SessionRepository#removeSession(String)
     */
    public synchronized static void removeSession(Session session) {
        removeSession(session.myName);
    }

    /**
     * clear repository fully
     */
    public synchronized static void clearAllSessions() {
        repository.clear();
    }
}
