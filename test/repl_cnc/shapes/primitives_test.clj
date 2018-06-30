(ns repl-cnc.shapes.primitives-test
  (:require [repl-cnc.shapes.primitives :as sut]
            [clojure.test :as t]))

(t/deftest stepovers-test
  (t/are [stepovers expected] (= stepovers expected)
    (sut/stepovers 1 1 3) [0 1 1]

    (sut/stepovers 1 1 -3) [0 -1 -1]

    (sut/stepovers 1 -1 -3) [0 -1 -1]

    (sut/stepovers 2 0.5 2.5) [0 0.5]

    (sut/stepovers 3 1 2) [0])

  (t/is (thrown? AssertionError (sut/stepovers 0 1 1))))

(t/deftest stepdowns-test
  (t/are [stepdowns expected] (= stepdowns expected)
    (sut/stepdowns 1 3) [1 1 1]

    (sut/stepdowns 1 -3) [-1 -1 -1]

    (sut/stepdowns 2 -2.5) [-2 -0.5]

    (sut/stepdowns 3 -2) [-2])

  (t/is (thrown? AssertionError (sut/stepdowns 0 1))))
