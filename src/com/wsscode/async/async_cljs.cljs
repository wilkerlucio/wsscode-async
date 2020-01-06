(ns com.wsscode.async.async-cljs
  (:require-macros [com.wsscode.async.async-cljs :refer [go-catch <?]])
  (:require [cljs.core.async :as async]
            [cljs.core.async.impl.protocols :as async.prot]
            [goog.object :as gobj]))

(def ^:dynamic *default-test-timeout* 2000)

(defn chan?
  "Check if c is a core.async channel."
  [c]
  (satisfies? async.prot/ReadPort c))

(defn promise?
  "Return true if X is a js obj with the property .then available as a fn."
  [x]
  (try
    (fn? (gobj/get x "then"))
    (catch :default _ false)))

(defn promise->chan
  "Converts promise p in a promise-chan. The response of this channel should be consumed
  using `consume-pair`."
  [p]
  (let [c (async/promise-chan)]
    (.then p
      #(async/put! c {:success %})
      #(async/put! c {:error %}))
    c))

(defn consumer-pair
  "Consume promise channel result pair."
  [resp]
  (if (contains? resp :error)
    (throw (:error resp))
    (:success resp)))

(defn error?
  "Returns true if err is an error object."
  [err]
  (instance? js/Error err))

(defn throw-err
  "Throw error x if x is an error."
  [x]
  (if (error? x)
    (throw x)
    x))
