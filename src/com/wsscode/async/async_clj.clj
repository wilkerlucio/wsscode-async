(ns com.wsscode.async.async-clj
  (:require [clojure.core.async :as async]
            [clojure.core.async.impl.protocols :as async.prot]
            [clojure.spec.alpha :as s]))

(defn chan?
  "Check if c is a core.async channel."
  [c]
  (satisfies? async.prot/ReadPort c))

(defmacro go
  "Same as `clojure.core.async/go`. Just a convenience place for it."
  [& body]
  `(async/go ~@body))

(defmacro go-loop
  "Same as `clojure.core.async/go-loop`. Just a convenience place for it."
  [bindings & body]
  `(async/go-loop bindings ~@body))

(defmacro thread
  "Same as `clojure.core.async/thread`. Just a convenience place for it."
  [& body]
  `(async/thread ~@body))

(defmacro catch-all
  "Catches all Throwable exceptions and returns them as-is."
  [& body]
  `(try
     ~@body
     (catch Throwable e# e#)))

(defmacro go-catch
  "Creates a go block that has a try/catch wrapping body, in case of errors the error
  flows up as data instead triggering the exception."
  [& body]
  `(async/go (catch-all ~@body)))

(defmacro thread-catch
  "Creates a thread that has a try/catch wrapping body, in case of errors the error
  flows up as data instead triggering the exception."
  [& body]
  `(async/thread (catch-all ~@body)))

(defmacro nil-safe-put!
  "Puts result of body on the provided channel if non-nil, else it closes it.
  A workaround to allow communicating nils."
  [ch & body]
  `(if-some [res# (catch-all ~@body)]
     (async/put! ~ch res#)
     (async/close! ~ch)))

(defmacro go-promise
  "Creates a go block using a promise channel, so the output of the go block can be
  read any number of times once ready."
  [& body]
  `(let [ch# (async/promise-chan)]
     (async/go (nil-safe-put! ch# ~@body))
     ch#))

(defmacro thread-promise
  "Creates a thread using a promise channel, so the output of the thread block can be
  read any number of times once ready."
  [& body]
  `(let [ch# (async/promise-chan)]
     (async/thread (nil-safe-put! ch# ~@body))
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
  "Same as clojure.core.async/<!. Just a convenience place for it."
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

(defmacro async-test
  "Ths is similar to the CLJS version for async test, but on the Clojure side the `async`
  helper doesn't exist, instead of using that we block on the wrapper go block. Example:

      (deftest async-test
        (wa/async-test
          (is (= 42 (<! (some-async-op)))))

  This will also add a timeout (default 2000ms), to change the timeout you can send
  a map with configuration after the test symbol, example:

      (deftest async-test
        (wa/async-test
          {::wa/timeout 5000} ; 5000ms timeout
          (is (= 42 (<! (some-async-op)))))
  "
  [& body]
  (let [[options body]
        (if (map? (first body))
          [(first body) (rest body)]
          [{} body])

        {::keys [timeout]} (merge {::timeout 2000} options)]
    `(let [timeout-ms# ~timeout]
       (async/<!!
         (async/go
           (let [timer# (clojure.core.async/timeout timeout-ms#)
                 [res# ch#] (clojure.core.async/alts! [(go-promise ~@body) timer#] :priority true)]
             (if (= ch# timer#)
               (clojure.test/is (= (str "Test timeout after " timeout-ms# "ms") false)))
             (if (error? res#)
               (clojure.test/is (= res# false)))
             res#))))))

(s/def ::go-try-stream-args
  (s/cat :params (s/and vector? #(= 2 (count %)))
         :body (s/+ any?)
         :catch (s/spec (s/cat :catch #{'catch}
                               :error-type any?
                               :error-var symbol?
                               :catch-body (s/+ any?)))))

(defmacro go-try-stream
  "If you want to read from a stream and catch errors in the messages that come
  from it, this helper is for you.

  The complication around adding try-catch on a go-loop is that we can't recur inside
  try/catch. This helper will create a structure around it that will catch errors and recur
  properly.

  Usage:

      (go-try-stream [value some-chan]
        (do-operation-here)
        (catch Throwable e
          (report-error e))"
  [& args]
  (let [{:keys [params body catch]} (s/conform ::go-try-stream-args args)
        {:keys [error-type error-var catch-body]} catch
        [binding-key binding-value] params]
    `(go-promise
       (let [continue*# (volatile! true)]
         (loop []
           (try
             (if-let [~binding-key (<? ~binding-value)]
               (do
                 ~@body)
               (vreset! continue*# false))
             (catch ~error-type ~error-var
               ~@catch-body))
           (if @continue*#
             (recur)))))))

(s/fdef go-try-stream
  :args ::go-try-stream-args
  :ret any?)

(defmacro deftest-async
  "Define an async test, this helper uses the clojure.test async feature, the user body
  will be wrapped around a `go` block automatically and the async done will be called
  after the go block finishes it's execution. Example:

      (wa/deftest-async async-test
        (is (= 42 (<! (some-async-op))))

  This will also add a timeout (default 2000ms), to change the timeout you can send
  a map with configuration after the test symbol, example:

      (wa/deftest-async async-test
        {::wa/timeout 5000} ; 5000ms timeout
        (is (= 42 (<! (some-async-op))))

  If you want to use this with a different `deftest` constructor, use the `async-test`
  macro."
  [sym & body]
  `(clojure.test/deftest ~sym
     (async-test ~@body)))
