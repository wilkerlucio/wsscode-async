(ns com.wsscode.async.processing
  (:require [clojure.core.async :as async :refer [<! chan go-loop]]
            [clojure.spec.alpha :as s]
            [#?(:clj  com.wsscode.async.async-clj
                :cljs com.wsscode.async.async-cljs)
             :refer [<!maybe go-promise]]))

(s/def ::channel any?)
(s/def ::request-id any?)
(s/def ::response-id ::request-id)
(s/def ::request-response any?)
(s/def ::timeout pos-int?)

(def ^:dynamic *default-timeout* 5000)

(defonce response-notifiers* (atom {}))

(defn random-request-id
  "Portable helper to generate random UUID."
  []
  #?(:clj  (java.util.UUID/randomUUID)
     :cljs (random-uuid)))

(defn await!
  "Wait for some async response, this returns a channel that will receive the data
  once the other side responds to the ::request-id.

  When message doesn't have a ::request-id this function is a noop, so its safe to
  call in the message return point of your code."
  [{::keys [request-id timeout request-response] :as msg}]
  #_[(s/keys :opt [::request-id ::timeout])
     => (? ::channel)]
  (if (and request-id (not request-response))
    (let [chan     (async/promise-chan)
          timeout' (or timeout *default-timeout*)
          timer    (async/timeout timeout')]
      (swap! response-notifiers* assoc request-id chan)
      (go-promise
        (let [[val ch] (async/alts! [chan timer] :priority true)]
          (swap! response-notifiers* dissoc request-id)
          (if (= ch timer)
            (ex-info "Response timeout" {:timeout      timeout'
                                         ::request-id  request-id
                                         :request-keys (keys msg)})
            val))))))

(defn capture-response!
  "Use this helper in the receiving side of events, right before sending to your original
  handler message. This will ensure that response messages are propagated back to the
  listener."
  [{::keys [response-id request-response]}]
  #_[(? (s/keys :opt [::response-id ::request-response]))
     => (s/or :captured true? :ignored nil?)]
  (when request-response
    (if-let [chan (get @response-notifiers* response-id)]
      (async/put! chan request-response)
      (print "Tried to notify unavailable responder" response-id))
    true))

(defn reply-message
  "Helper to make a response map for a given message with a request-id.

  Use this to generate response data from async events."
  [{::keys [request-id]} value]
  #_[(s/keys :req [::request-id]) any?
     => (s/keys :req [::response-id ::request-response])]
  {::response-id      request-id
   ::request-response value})

(defn event-queue!
  "Returns a callback handler that will respond to events in a serialized queue. When
  the user handler returns a channel, the next message will wait until the current one
  finished processing before moving on. Use this on callback handlers that need serialization
  to avoid concurrency issues."
  ([handler] #_[fn? => any?]
   (event-queue! {} handler))
  ([{::keys [channel]} handler]
   #_[(s/keys :opt [::channel]) fn?
      => any?]
   (let [channel' (or channel (chan (async/dropping-buffer 1024)))]
     (go-loop []
       (when-let [args (<! channel')]
         (<!maybe (apply handler args))
         (recur)))

     (fn [& args] (async/put! channel' args)))))

