(ns com.wsscode.async.async-cljs-test
  (:require [clojure.test :refer [is are run-tests async testing deftest]]
            [clojure.core.async :as async :refer [go <!]]
            [com.wsscode.async.async-cljs :as wa :refer [go-promise <!p <? <?maybe]]))

(defn fail-ch
  ([] (fail-ch (ex-info "foo" {:bar "baz"})))
  ([err]
   (wa/go-promise (throw err))))

(deftest test-chan?
  (is (true? (wa/chan? (async/chan))))
  (is (true? (wa/chan? (async/promise-chan))))
  (is (false? (wa/chan? 3))))

(deftest test-promise?
  (is (true? (wa/promise? (js/Promise. (fn [_])))))
  (is (false? (wa/promise? 3))))

(wa/deftest-async test-go
  (is (= (<! (wa/go "foo"))
         "foo")))

(wa/deftest-async test-go-catch
  (is (= (<! (wa/go-catch "foo"))
         "foo"))
  (let [err (ex-info "foo" {:bar "baz"})]
    (is (= (<! (fail-ch err))
           err))))

(wa/deftest-async test-go-promise
  (let [ch (wa/go-promise "foo")]
    (is (= (<! ch)
           "foo"))
    (is (= (<! ch)
           "foo")))
  (let [err (ex-info "foo" {:bar "baz"})
        ch  (fail-ch err)]
    (is (= (<! ch)
           err))
    (is (= (<! ch)
           err)))

  (testing "handle nil by closing the channel"
    (is (= (<! (wa/go-promise nil))
           nil)))

  (testing "can return false"
    (is (= (<! (wa/go-promise false))
           false))))

(deftest test-error?
  (is (false? (wa/error? 1)))
  (is (false? (wa/error? "string")))
  (is (false? (wa/error? true)))
  (is (false? (wa/error? nil)))
  (is (false? (wa/error? 3.5)))
  (is (true? (wa/error? (ex-info "foo" {})))))

(wa/deftest-async test-<!
  (is (= (<! (wa/go (wa/<! (wa/go "foo"))))
         "foo")))

(wa/deftest-async test-<!p
  (is (= (wa/<!p (js/Promise.resolve "foo"))
         "foo")))

(wa/deftest-async test-<?
  (let [err (ex-info "foo" {:bar "baz"})]
    (is (= (<! (wa/go-promise
                  (wa/<? (fail-ch err))))
           err))))

(wa/deftest-async test-<!maybe
  (is (= (<! (go (wa/<!maybe "foo")))
         "foo"))

  (is (= (<! (go (wa/<!maybe (go "foo"))))
         "foo")))

(wa/deftest-async test-<?maybe
  (is (= (<! (go (wa/<?maybe "foo")))
         "foo"))

  (is (= (<! (go (wa/<?maybe (go "foo"))))
         "foo"))

  (is (= (<! (go (wa/<?maybe (js/Promise.resolve "foo"))))
         "foo"))

  (let [err (ex-info "foo" {:bar "baz"})]
    (is (= (<! (wa/go-promise
                  (wa/<?maybe (fail-ch err))))
           err))))

(wa/deftest-async test-let-chan
  (is (= (wa/let-chan [x "foo"]
           x)
         "foo"))

  (is (= (<! (wa/let-chan [x (go "foo")]
                x))
         "foo"))

  (let [err1 (ex-info "foo" {})
        err2 (ex-info "foo2" {})]
    (is (= (<! (wa/let-chan [x (fail-ch err1)]
                  (throw err2)
                  x))
           err1))))

(wa/deftest-async test-let-chan*
  (is (= (wa/let-chan* [x "foo"]
           x)
         "foo"))

  (is (= (<! (wa/let-chan* [x (go "foo")]
               x))
         "foo"))

  (let [err1 (ex-info "foo" {})
        err2 (ex-info "foo2" {})]
    (is (= (<! (wa/let-chan* [x (fail-ch err1)]
                 (throw err2)
                 x))
           err2))))

(wa/deftest-async timeout-chan-test
  (try
    (wa/<? (wa/timeout-chan 100 (go (<! (async/timeout 500)))))
    (is (= "Timeout was expected" true))
    (catch :default _
      (is (= true true)))))

(wa/deftest-async pulling-retry-test
  (testing "sync body"
    (let [x (atom nil)]
      (go
        (<! (async/timeout 300))
        (reset! x 10))
      (is (= (<! (wa/pulling-retry {::wa/done? number?} @x)) 10))))

  (testing "async body"
    (let [x (atom nil)]
      (go
        (<! (async/timeout 300))
        (reset! x 10))
      (is (= (<! (wa/pulling-retry {::wa/done? number?} (go @x))) 10))))

  (testing "quick done syntax"
    (let [x (atom nil)]
      (go
        (<! (async/timeout 300))
        (reset! x 10))
      (is (= (<! (wa/pulling-retry number? @x)) 10))))

  (testing "stop after timeout"
    (let [x (atom 0)]
      (<! (wa/pulling-retry {::wa/done?   neg?
                             ::wa/timeout 100}
            (go
              (<! (async/timeout 200))
              (swap! x + 10)
              @x)))

      (<! (async/timeout 500))

      (is (= @x 10)))))

(wa/deftest-async go-try-stream-test
  (is (= (let [vals (atom [])
               c    (async/chan 50)]
           (async/onto-chan! c [:a (ex-info "err" {}) :b] true)
           (<! (wa/go-try-stream [value c]
                 (swap! vals conj value)
                 (catch :default e
                   (swap! vals conj (ex-message e)))))
           @vals)
         [:a "err" :b])))
