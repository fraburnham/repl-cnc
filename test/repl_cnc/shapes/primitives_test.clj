(ns repl-cnc.shapes.primitives-test
  (:require [repl-cnc.shapes.primitives :as sut]
            [repl-cnc.gcode :as gcode]
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
(t/deftest arc-pocket-test
  (let [config {:feedrate 100
                :plunge-feedrate 100
                :plunge-depth 1
                :tool-width 1}]
    (t/are [radius z-end expected] (= (sut/arc-pocket config radius z-end) expected)
      5 -1 [(gcode/relative-move config 0 0 -1)
            (gcode/arc config 0 0 0 5)
            (gcode/relative-move config 0 0 1)])))

(t/deftest circle-pocket-test
  (let [config {:feedrate 100
                :plunge-feedrate 100
                :plunge-depth 1
                :tool-width 1}]
    (t/are [radius z-end stepover-amount expected] (= (sut/circle-pocket config radius z-end stepover-amount) expected)
      5 -1 1 [(gcode/relative-move config 0 0 0)  ; 0th y step
              (gcode/relative-move config 0 0 -1)
              (gcode/arc config 0 0 0 0)  ; 0th radius
              (gcode/relative-move config 0 0 1)
              
              (gcode/relative-move config 0 1 0)  ; move (* stepover-amount tool-width)
              (gcode/relative-move config 0 0 -1)
              (gcode/arc config 0 0 0 -1)
              (gcode/relative-move config 0 0 1)

              (gcode/relative-move config 0 1 0)  ; final, partial stepover
              (gcode/relative-move config 0 0 -1)
              (gcode/arc config 0 0 0 -2)
              (gcode/relative-move config 0 0 1)]

      10 -1 1 [(gcode/relative-move config 0 0 0)  ; 0th y step
               (gcode/relative-move config 0 0 -1)
               (gcode/arc config 0 0 0 0)  ; 0th radius
               (gcode/relative-move config 0 0 1)
              
               (gcode/relative-move config 0 1 0)  ; move (* stepover-amount tool-width)
               (gcode/relative-move config 0 0 -1)
               (gcode/arc config 0 0 0 -1)
               (gcode/relative-move config 0 0 1)

               (gcode/relative-move config 0 1 0)
               (gcode/relative-move config 0 0 -1)
               (gcode/arc config 0 0 0 -2)
               (gcode/relative-move config 0 0 1)

               (gcode/relative-move config 0 1 0)
               (gcode/relative-move config 0 0 -1)
               (gcode/arc config 0 0 0 -3)
               (gcode/relative-move config 0 0 1)

               (gcode/relative-move config 0 1 0)
               (gcode/relative-move config 0 0 -1)
               (gcode/arc config 0 0 0 -4)
               (gcode/relative-move config 0 0 1)

               (gcode/relative-move config 0 0.5 0)
               (gcode/relative-move config 0 0 -1)
               (gcode/arc config 0 0 0 -4.5)
               (gcode/relative-move config 0 0 1)])))
