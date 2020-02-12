(ns com.wsscode.async.async-clj
  (:require [clojure.core.async :as async]
            [clojure.core.async.impl.protocols :as async.prot]))

(defn chan?
  "Check if c is a core.async channel."
  [c]
  (satisfies? async.prot/ReadPort c))

(defmacro go
  "Same as clojure.core.async/go. Just a convenience place for it."
  [& body]
  `(async/go ~@body))

(defmacro go-catch
  "Creates a go block that has a try/catch wrapping body, in case of errors the error
  flows up as data instead triggering the exception."
  [& body]
  `(async/go
     (try
       ~@body
       (catch Throwable e# e#))))

(defmacro go-promise
  "Creates a go block using a promise channel, so the output of the go block can be
  read any number of times once ready."
  [& body]
  `(let [ch# (async/promise-chan)]
     (async/go
       (let [res# (try
                    ~@body
                    (catch Throwable e# e#))]
         (if (some? res#)
           (async/put! ch# res#)
           (async/close! ch#))))
     ch#))

(defn error?
  "Returns true if err is an error object."
  [err]
  (instance? Throwable err))

(defn throw-err
  "Throw error x if x is an error."
  [x]
  (if (error? x)
    (throw x)
    x))

(defmacro <!
  "Same as clojure.core.async/<!!. Just a convenience place for it."
  [& body]
  `(async/<! ~@body))

(defmacro <!!
  "Same as clojure.core.async/<!!. Just a convenience place for it."
  [& body]
  `(async/<!! ~@body))

(defmacro <?
  "Reads a channel value and check if it is an error, in case it is, throw the error."
  [ch]
  `(throw-err (async/<! ~ch)))

(defmacro <?!
  "Reads a channel value and check if it is an error, in case it is, throw the error."
  [ch]
  `(throw-err (async/<!! ~ch)))

(defmacro <!maybe
  "Reads a channel if it is a channel, if it's not a channel, return x."
  [x]
  `(let [res# ~x]
     (if (chan? res#) (async/<! res#) res#)))

(defmacro <!!maybe
  "Like <!maybe but will block the thread."
  [x]
  `(let [res# ~x]
     (if (chan? res#) (async/<!! res#) res#)))

(defmacro <?maybe
  "A combination of <!maybe and <?."
  [x]
  `(let [res# ~x]
     (if (chan? res#) (<? res#) res#)))

(defmacro <?!maybe
  "A combination of <!maybe and <?."
  [x]
  `(let [res# ~x]
     (if (chan? res#) (<?! res#) res#)))

(defmacro let-chan
  "Handles a possible channel on value."
  [[name value] & body]
  `(let [res# ~value]
     (if (chan? res#)
       (go-catch
         (let [~name (<? res#)]
           ~@body))
       (let [~name res#]
         ~@body))))

(defmacro let-chan*
  "Like let-chan, but async errors will be returned instead of propagated"
  [[name value] & body]
  `(let [res# ~value]
     (if (chan? res#)
       (go-catch
         (let [~name (async/<! res#)]
           ~@body))
       (let [~name res#]
         ~@body))))
