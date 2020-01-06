(defproject com.wsscode/async "1.0.0"
  :description "Helpers for core.async."
  :url "https://github.com/wilkerlucio/wsscode-async"
  :license {:name "MIT"
            :url  "https://opensource.org/licenses/MIT"}

  :source-paths ["src"]

  :dependencies [[org.clojure/core.async "0.6.532"]

                 ; provided

                 [org.clojure/clojure "1.10.0" :scope "provided"]
                 [org.clojure/clojurescript "1.10.597" :scope "provided"]]

  :jar-exclusions [#"node-modules/.+"]

  :deploy-repositories [["releases" :clojars]]

  :aliases {"pre-release"  [["vcs" "assert-committed"]
                            ["change" "version" "leiningen.release/bump-version" "release"]
                            ["vcs" "commit"]
                            ["vcs" "tag" "v"]]

            "post-release" [["change" "version" "leiningen.release/bump-version"]
                            ["vcs" "commit"]
                            ["vcs" "push"]]})
