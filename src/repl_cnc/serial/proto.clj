(ns repl-cnc.serial.proto)

(defprotocol CNCPortReader
  (read-data [reader] "Read bytes from port until end of message"))

(defprotocol CNCPortWriter
  (write-data [writer data] "Write data to port"))

(defprotocol CNCCommandSender
  (send-command [sender data] "Send command and return response"))

(defprotocol CNCJobExecutor
  (execute-job [executor job] "Send a job (seq of gcode) to the controller"))
