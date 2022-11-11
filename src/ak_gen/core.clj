(ns ak-gen.core
  (:gen-class)
  (:require [clj-path.core :as path]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.math.combinatorics :as combo]
            [clojure.string :as str]
            [environ.core :refer [env]]))

(defn modkey->str [modkey]
  (str "<" (name modkey) ">"))

(defn ->script [{:as hotkey-info :keys [send-modifiers keys]}]
  (str "keyboard.send_keys(\""
       (when-not (empty? send-modifiers)
         (str (str/join "+" (map modkey->str send-modifiers)) "+"))
       keys
       "\")"))

(defn write-script [{:as hotkey-info :keys [dirpath basename]}]
  (let [p       (path/join dirpath (str basename ".py"))
        content (->script hotkey-info)]
    (println p)
    (spit p content)))

(defn ->hotkey-settings [{:as hotkey-info :keys [modifiers send-modifiers hotkey]}]
  {:description    (str (str/join "_" (map name send-modifiers)) "_" hotkey)
   :store          {}
   :showInTrayMenu false
   :type           "script"
   :modes          [3]
   :usageCount     0
   :hotkey         {:modifiers (map modkey->str (concat modifiers send-modifiers)) :hotKey hotkey}
   :prompt         false
   :filter         {:regex nil :isRecursive false}
   :omitTrigger    false
   :abbreviation   {:abbreviations []
                    :backspace     true
                    :ignoreCase    false
                    :immediate     false
                    :triggerInside false
                    :wordChars     "[\\w]"}})

(defn write-hotkey-settings [{:as hotkey-info :keys [dirpath basename]}]
  (let [p       (path/join dirpath (str "." basename ".json"))
        content (json/write-str (->hotkey-settings hotkey-info))]
    (spit p content)))

(defn write-hotkey-file [{:as hotkey-info :keys [modifiers send-modifiers hotkey keys blind? dirpath]}]
  (if blind?
    (doseq [mods (combo/subsets [:ctrl :shift :alt])]
      (write-hotkey-file (-> hotkey-info
                             (assoc ,, :blind? false)
                             (assoc ,, :send-modifiers (vec (set mods))))))
    (let [basename (str (when (not-empty send-modifiers)
                          (str (str/join "_" (map name send-modifiers)) "_"))
                        hotkey)]
      (write-script (assoc hotkey-info :basename basename))
      (write-hotkey-settings (assoc hotkey-info :basename basename)))))

(def folder-settings
  {:type           "folder"
   :title          ".ak-gen"
   :modes          []
   :usageCount     0
   :showInTrayMenu false
   :abbreviation   {:abbreviations []
                    :backspace     true
                    :ignoreCase    false
                    :immediate     false
                    :triggerInside false
                    :wordChars     "[\\w]"}
   :hotkey         {:modifiers [] :hotKey nil}
   :filter         {:regex nil :isRecursive false}})

(defn write-hotkey-files
  "
  `hotkeys-info` is a collection of `hotkey-info`.
  `hotkey-info` is a map composed of following key-value pair:

  | modifiers | Vector of hotkey modifiers. Modifiers are #{:ctrl :shift :alt :hyper :meta}. |
  | hotkey    | Main key of the hotkey.                                                      |
  | keys      | Keys to be sent by the script.                                               |
  | blind?    | If true, all modifier keys are passed through to the hotkey.                 |
  "
  [hotkeys-info]
  (let [dirpath (or (env :ak-gen-dir) (str (env :home) "/.ak-gen"))]
    (.mkdir (io/as-file dirpath))
    (spit (path/join dirpath ".folder.json") (json/write-str folder-settings))
    (doseq [hotkey-info hotkeys-info]
      (write-hotkey-file (assoc hotkey-info :dirpath dirpath)))))

(defn -main [& [input]]
  (let [lines        (str/split (slurp input) #"\r?\n")
        hotkeys-info (for [line lines]
                       (let [[modifiers hotkey keys blind?] (str/split line #",")]
                         {:modifiers (map keyword (str/split modifiers #":"))
                          :hotkey    hotkey
                          :keys      keys
                          :blind?    (not (or (empty? blind?)
                                              (= "false" blind?)
                                              (= "FALSE" blind?)))}))]
    (write-hotkey-files hotkeys-info)))

(comment
  (write-hotkey-files
   [{:modifiers [:hyper]
     :hotkey "i"
     :keys "<up>"
     :blind? true}])
  (-main "/home/nankk/tmp/autokey/input/ak-gen-input.csv"))
