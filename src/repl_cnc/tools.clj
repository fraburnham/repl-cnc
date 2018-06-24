(ns repl-cnc.tools
  (:require [repl-cnc.gcode :as gcode]))

(defn use-spindle
  [config steps]
  (as-> [(gcode/start-spindle config)] *
    (apply conj * steps)
    (conj * gcode/stop-spindle)))

;; once I get a laser head...
