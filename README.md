# SessionRepository
A simple data repository to share data among different classes in a project.

This module is basically a key-value data repository and it is normaly due to hold data. I just modified it to support observable pattern too. So, like [Event Bus](http://www.github.com/greenrobot/eventbus) you can publish data for listeners too, but listeners get notified by given key instead of data type.

## How to use this library
1.Download module, Unzip it and add it to your project as gradle module
2.Catch a session
```java
Session session = SessionRepository.getSession("mySession");

```
or use default session
```java

Session session = SessionRepository.getDefaultSession();

```
3.Now you can put/get a key-value into/from the session
```java

session.put("key1","some data"); //data can be any type
Object obj = session.get("key1","default value if the given key doesn't exist");

```
Also, you may want to use it to notify listener.
1.After obtaining a session, register listener class:
```java
session.register(yourClass); //usually 'this'

```
2.Subscribe a __public__ method as listener:
```java

@Subscribe(keyword = "aKey", mainThread = true, deleteKey = true)
public void listener1(Object obj) {  //Please notice that the obj type would be what you have put in session
   ...
}

```
> mainThread and deleteKey are true by default.

> mainThread determines whether to run listener method in UI thread.

> the given key would be deleted just after first invoke, if you are willing to keep mentioned key-value in the session, pass false for deleteKey.

3.To invoke listeners, put data in session like before:
```java

session.put("aKey","some data");

```
