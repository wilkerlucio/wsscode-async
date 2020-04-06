(ns com.wsscode.async.processing-test
  (:require [clojure.test :refer [deftest is are run-tests testing]]
            [com.wsscode.async.processing :as wap]
            [#?(:clj  com.wsscode.async.async-clj
                :cljs com.wsscode.async.async-cljs)
             :refer [go-promise <! <? <?maybe deftest-async]]
            [clojure.core.async :as async]))

(deftest-async event-queue!-test
  (let [out      (atom [])
        handler  (fn [duration]
                   (go-promise
                     (swap! out conj (do (<! (async/timeout duration))
                                         duration))))
        handler' (wap/event-queue! handler)]
    (handler' 300)
    (handler' 100)
    (<! (async/timeout 500))
    (is (= @out [300 100]))))

(deftest-async id-callbacks-test
  (let [msg     {::wap/request-id (wap/random-request-id)}
        request (wap/await! msg)]
    (wap/capture-response! (wap/reply-message msg "value"))
    (is (= (<? request) "value"))))
