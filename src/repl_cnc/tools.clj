(ns repl-cnc.tools
  (:require [repl-cnc.gcode :as gcode]))

(defn use-spindle
  [config steps]
  (as-> [(gcode/start-spindle config)] *
    (apply conj * steps)
    (conj * gcode/stop-spindle)))

(defn working-tool-width
  [config stepover-amount]
  (* (:tool-width config)
     stepover-amount))
