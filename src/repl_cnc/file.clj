(ns repl-cnc.file)

(defn gcode? [line]
  (or (.startsWith line "G")
      (.startsWith line "M")))

(defn read-gcode [filename]
  (with-open [reader (clojure.java.io/reader filename)]
    (doall (filter gcode? (line-seq reader)))))
