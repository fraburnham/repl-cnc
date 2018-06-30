(ns repl-cnc.shapes.primitives-test
  (:require [repl-cnc.shapes.primitives :as sut]
            [clojure.test :as t]))

(t/deftest stepovers-happy-path
  (t/are [stepovers expected] (= stepovers expected)
    (sut/stepovers 1 1 3) [1 1 1]

    (sut/stepovers 1 1 -3) [-1 -1 -1]

    (sut/stepovers 1 -1 -3) [-1 -1 -1]

    (sut/stepovers 2 0.5 2.5) [1.0 1.0 0.5])

  (t/is (thrown? AssertionError (sut/stepovers 0 1 1))))
