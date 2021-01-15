(ns com.wsscode.async.coll-test
  (:require [clojure.test :refer [deftest is are run-tests testing]]
            [#?(:clj  com.wsscode.async.async-clj
                :cljs com.wsscode.async.async-cljs)
             :refer [go-promise <! <? deftest-async]]
            [com.wsscode.async.coll :as a.coll]))

(deftest-async reduce-async-test
  (testing "sync iterator"
    (is (= (<? (a.coll/reduce-async
                 (fn [n v] (+ n v))
                 0
                 [1 2]))
           3)))

  (testing "async iterator"
    (is (= (<? (a.coll/reduce-async
                 (fn [n v] (go-promise (+ n v)))
                 0
                 [1 2]))
           3))))

(deftest-async reduce-kv-async-test
  (testing "sync iterator"
    (is (= (<? (a.coll/reduce-kv-async
                 (fn [n _k v] (+ n v))
                 0
                 {:a 1 :b 2}))
           3)))

  (testing "async iterator"
    (is (= (<? (a.coll/reduce-kv-async
                 (fn [n _k v] (go-promise (+ n v)))
                 0
                 {:a 1 :b 2}))
           3))))
