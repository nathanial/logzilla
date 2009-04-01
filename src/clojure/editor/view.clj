(ns editor.view
  (:use editor.model util gutil global)
  (:import (javax.swing JSlider JTable)
	   (javax.swing.table DefaultTableModel)))

(defn create-depth-slider [editor-data]
  (let [slider (new JSlider 0 (:slider-notches editor-data) 0)]
    (doto slider
      (.setOrientation JSlider/VERTICAL))))

(defn create-table [editor-data]
  (let [{:keys [curves index]} editor-data
	model (new DefaultTableModel)
	table (new JTable model)]    
    (.addColumn model "x" (into-array Object (reverse (:data index))))
    (doseq [curve curves]
      (.addColumn model (get-in curve [:descriptor :mnemonic]) (into-array Object (reverse (:data curve)))))
    table))

(defn create-save-button [editor-data]
  (button "Save" (fn [e] nil)))

(defn create-merge-button [editor-data]
  (button "Merge" (fn [e] nil)))