(ns com.wsscode.async.async-clj-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async :refer [go <!!]]
            [com.wsscode.async.async-clj :as wa :refer [go-promise <?]])
  (:import (clojure.lang ExceptionInfo)))

(defn fail-ch
  ([] (fail-ch (ex-info "foo" {:bar "baz"})))
  ([err]
   (wa/go-promise (throw err))))

(deftest test-chan?
  (is (= (wa/chan? (async/chan)) true))
  (is (= (wa/chan? (async/promise-chan)) true))
  (is (= (wa/chan? 3) false)))

(deftest test-go
  (is (= (<!! (wa/go "foo"))
         "foo")))

(deftest test-go-catch
  (is (= (<!! (wa/go-catch "foo"))
         "foo"))
  (let [err (ex-info "foo" {:bar "baz"})]
    (is (= (<!! (fail-ch err))
           err))))

(deftest test-go-promise
  (let [ch (wa/go-promise "foo")]
    (is (= (<!! ch)
           "foo"))
    (is (= (<!! ch)
           "foo")))
  (let [err (ex-info "foo" {:bar "baz"})
        ch  (fail-ch err)]
    (is (= (<!! ch)
           err))
    (is (= (<!! ch)
           err)))

  (testing "handle nil by closing the channel"
    (is (= (<!! (wa/go-promise nil))
           nil))))

(deftest test-error?
  (is (false? (wa/error? 1)))
  (is (false? (wa/error? "string")))
  (is (false? (wa/error? true)))
  (is (false? (wa/error? nil)))
  (is (false? (wa/error? 3.5)))
  (is (true? (wa/error? (ex-info "foo" {})))))

(deftest test-<!
  (is (= (<!! (wa/go (wa/<! (wa/go "foo"))))
         "foo")))

(deftest test-<!!
  (is (= (wa/<!! (wa/go "foo"))
         "foo")))

(deftest test-<?
  (let [err (ex-info "foo" {:bar "baz"})]
    (is (= (<!! (wa/go-promise
                  (wa/<? (fail-ch err))))
           err))))

(deftest test-<!maybe
  (is (= (<!! (go (wa/<!maybe "foo")))
         "foo"))

  (is (= (<!! (go (wa/<!maybe (go "foo"))))
         "foo")))

(deftest test-<!!maybe
  (is (= (wa/<!!maybe "foo")
         "foo"))

  (is (= (wa/<!!maybe (go "foo"))
         "foo")))

(deftest test-<?maybe
  (is (= (<!! (go (wa/<?maybe "foo")))
         "foo"))

  (is (= (<!! (go (wa/<?maybe (go "foo"))))
         "foo"))

  (let [err (ex-info "foo" {:bar "baz"})]
    (is (= (<!! (wa/go-promise
                  (wa/<?maybe (fail-ch err))))
           err))))

(deftest test-<?!maybe
  (is (= (wa/<?!maybe "foo")
         "foo"))

  (is (= (wa/<?!maybe (go "foo"))
         "foo"))

  (is (thrown?
        ExceptionInfo
        (wa/<?!maybe (fail-ch)))))

(deftest test-let-chan
  (is (= (wa/let-chan [x "foo"]
           x)
         "foo"))

  (is (= (<!! (wa/let-chan [x (go "foo")]
                x))
         "foo"))

  (let [err1 (ex-info "foo" {})
        err2 (ex-info "foo2" {})]
    (is (= (<!! (wa/let-chan [x (fail-ch err1)]
                  (throw err2)
                  x))
           err1))))

(deftest test-let-chan*
  (is (= (wa/let-chan* [x "foo"]
           x)
         "foo"))

  (is (= (<!! (wa/let-chan* [x (go "foo")]
                x))
         "foo"))

  (let [err1 (ex-info "foo" {})
        err2 (ex-info "foo2" {})]
    (is (= (<!! (wa/let-chan* [x (fail-ch err1)]
                  (throw err2)
                  x))
           err2))))

(defn async-divide [x y]
  ; <1>
  (go-promise
    (/ x y)))

(defn run [d]
  (go-promise
    (try
      ; <2>
      (<? (async-divide 6 d))
      (catch Throwable _
        "ERROR"))))

(comment
  (<!! (run 2)) ; => 3
  (<!! (run 0)) ; => "ERROR"
  )
