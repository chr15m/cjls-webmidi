(ns ^:figwheel-no-load webmidi.dev
  (:require
    [webmidi.core :as core]
    [devtools.core :as devtools]))


(enable-console-print!)

(devtools/install!)

(core/init!)
