(ns agent2.examples
  (:require [clojure.test :refer :all]
            [agent2.core :refer :all]))

(defn get-agent-value
  [agent-value ctx-atom]
  (reply ctx-atom agent-value))

(def agent42 (agent 42))
(def r42 (request-call agent42 get-agent-value))
(deftest test-get-agent-value
  (is (= r42 42)))

(defn get-indirect
  [_ ctx-atom agnt]
  (request ctx-atom agnt
           get-agent-value ()
           (fn [result] (reply ctx-atom result))))

(def agent99 (agent nil))
(def r99 (request-call agent99 get-indirect agent42))
(deftest test-get-indirect
  (is (= r99 42)))
