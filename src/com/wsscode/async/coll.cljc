(ns com.wsscode.async.coll
  "Collection helpers for async operations."
  (:require
    [#?(:clj  com.wsscode.async.async-clj
        :cljs com.wsscode.async.async-cljs)
     :refer [go-promise <? <?maybe <!maybe]]
    [com.wsscode.misc.coll :as coll]))

(defn reduce-async
  "Like reduce, but when some iteration item return a channel, it waits on its response
  and use that to continue the process. Always returns a promise channel."
  [f init coll]
  (go-promise
    (let [it (coll/iterator coll)]
      (loop [out init]
        (if (.hasNext it)
          (let [entry (.next it)]
            (recur (<?maybe (f out entry))))
          out)))))

(defn reduce-kv-async
  "Like reduce-kv, but when some iteration item return a channel, it waits on its response
  and use that to continue the process. Always returns a promise channel."
  [f init coll]
  (go-promise
    (let [it (coll/iterator coll)]
      (loop [out init]
        (if (.hasNext it)
          (let [entry (.next it)
                k     (key entry)
                v     (val entry)]
            (recur (<?maybe (f out k v))))
          out)))))
