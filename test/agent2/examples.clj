(ns agent2.examples
  (:require [clojure.test :refer :all]
            [agent2.core :refer :all]))

(defn get-agent-value
  [agent-value ctx-atom]
  (reply ctx-atom agent-value))

(def agent42 (agent 42))
(def r42 @(request-promise agent42 get-agent-value))
(deftest test-get-agent-value
  (is (= r42 42)))
