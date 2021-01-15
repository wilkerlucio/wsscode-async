# Change Log

## 2021.01.15
- Add `coll` namespace for collection utils with async process

## 2020.01.14
- Provide clj-kondo exports for lint

## 1.0.11
- Release using Leiningen to get cljdoc back to work.

## 1.0.10
- BREAKING: use namespaced options in `pulling-retry`
- Support simpler interface on `pulling-retry` - `(pulling-retry doner-check (ops))`

## 1.0.9 -- Broken interface, avoid using this version
- Add `timeout-chan`
- Add `pulling-retry`

## 1.0.8
- Fixes `go-loop` wrappers, thanks clj-kondo!

## 1.0.7
- Change `await!` to skip messages that already have a reply

## 1.0.6
- Important bug fix: `1.0.5` introduced an error by requiring `clojure.core.async`
 instead of `cljs.core.async` for macros, this could break some builds in very 
 unexpected ways, this update is highly encouraged.

## 1.0.5] - BROKEN, DON'T US
- Add `go-try-stream` helper

## 1.0.4
- Add `go-loop` convenience method
- Add `com.wsscode.async.processing` namespace
- Add `deftest-async` on the clojure side

## 1.0.3
- Add `thread` helpers in the `clj` side

## 1.0.2
- Fix `go-promise` on both clj and cljs to allow returning `false`
