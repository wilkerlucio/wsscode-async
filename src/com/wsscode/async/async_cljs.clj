(ns com.wsscode.async.async-cljs
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as s]))

(defmacro go
  "Same as cljs.core.async/go. Just a convenience place for it."
  [& body]
  `(async/go ~@body))

(defmacro go-loop
  "Same as `clojure.core.async/go-loop`. Just a convenience place for it."
  [bindings & body]
  `(async/go-loop bindings ~@body))

(defmacro go-catch
  "Creates a go block that has a try/catch wrapping body, in case of errors the error
  flows up as data instead triggering the exception."
  [& body]
  `(async/go
     (try
       ~@body
       (catch :default e# e#))))

(defmacro go-promise
  "Creates a go block using a promise channel, so the output of the go block can be
  read any number of times once ready."
  [& body]
  `(let [ch# (cljs.core.async/promise-chan)]
     (async/go
       (if-some [res# (try
                        ~@body
                        (catch :default e# e#))]
         (cljs.core.async/put! ch# res#)
         (cljs.core.async/close! ch#)))
     ch#))

(defmacro <!
  "Same as clojure.core.async/<!!. Just a convenience place for it."
  [& body]
  `(cljs.core.async/<! ~@body))

(defmacro <!p
  "Similar to core.async <!, but instead of taking a channel, <!p takes a Javascript
  Promise, converts to a channel and reads, this allows the use of regular JS promises
  inside go blocks using await like syntax.

  Example:

      (go ; start with go block
        (-> (js/fetch \"some-url\") <!p ; call fetch and await for response
            .text <!p ; await for text body reading
            js/console.log))"
  [promise]
  `(consumer-pair (cljs.core.async/<! (promise->chan ~promise))))

(defmacro <?
  "Reads a channel value and check if it is an error, in case it is, throw the error."
  [ch]
  `(throw-err (cljs.core.async/<! ~ch)))

(defmacro <?maybe
  "Tries to await for a value, first if checks if x is a channel, if so will read
  on it; then it checks if it's a JS promise, if so will convert it to a channel
  and read from it. Otherwise returns x as is."
  [x]
  `(let [res# ~x]
     (cond
       (chan? res#)
       (<? res#)

       (promise? res#)
       (<!p res#)

       :else
       res#)))

(defmacro <!maybe
  "Reads a channel if it is a channel, if it's not a channel, return x."
  [x]
  `(let [res# ~x]
     (if (chan? res#) (cljs.core.async/<! res#) res#)))

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
         (let [~name (cljs.core.async/<! res#)]
           ~@body))
       (let [~name res#]
         ~@body))))

(defmacro async-test
  "Creates an async block on the test, this helper uses the cljs.test async feature, the user body
  will be wrapped around a `go` block automatically and the async done will be called
  after the go block finishes it's execution. Example:

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
    `(cljs.test/async done#
       (let [timeout-ms# ~timeout]
         (async/go
           (let [timer# (cljs.core.async/timeout timeout-ms#)
                 [res# ch#] (cljs.core.async/alts! [(go-promise ~@body) timer#] :priority true)]
             (if (= ch# timer#)
               (cljs.test/is (= (str "Test timeout after " timeout-ms# "ms") false)))
             (if (error? res#)
               (cljs.test/is (= res# false)))
             (done#)
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
        (catch :default e
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
  "Define an async test, this helper uses the cljs.test async feature, the user body
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
  `(cljs.test/deftest ~sym
     (async-test ~@body)))
