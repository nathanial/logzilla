(ns editor.controller
  (:require lasso
	    [editor.slider.controller :as slider-controller]
	    [editor.table.controller :as table-controller]
	    [chart.controller :as chart-controller])
  (:use editor.model editor.view util global gutil curves chart.controller)
  (:import (javax.swing.event TableModelListener ChangeListener)
	   (javax.swing JFrame JScrollPane JToolBar JButton JToggleButton 
			ButtonGroup ImageIcon JPanel
			ScrollPaneConstants)
	   (java.awt Dimension Color)
	   (org.jfree.data Range)
	   (org.jfree.chart ChartMouseListener)
	   (net.miginfocom.swing MigLayout)))

(defn not-dragging-anything [editor]
  (dosync
   (let [chart (:chart @editor)
	 dragged-entity (:dragged-entity @chart)]
     (nil? dragged-entity))))

(defn init-frame [lasfile curve]
  (let [name (get-in @curve [:descriptor :mnemonic])
	frame (new JFrame (str (:name @lasfile) " " name))]
    frame))

(defn save [editor]
  (dosync
   (let [chart (:chart @editor)]
     (chart-controller/save-chart chart))))


(defn init-table-panel [slider table]
  (let [panel (JPanel. (MigLayout. "ins 0"))]
    (doto panel
      (.add (:widget @slider) "pushy, growy")
      (.add (:pane @table) "push, grow"))))

(defn init-main-panel [chart table-panel left-toolbar right-toolbar]
  (let [left-panel (JPanel. (MigLayout. "ins 0"))
	right-panel (JPanel. (MigLayout. "ins 0"))
	main-panel (JPanel. (MigLayout. "ins 0"))]
    (swing 
     (doto left-panel
       (.add table-panel "push, grow"))

     (let [chart-panel (:chart-panel @chart)]
       (doto right-panel
	 (.add right-toolbar "pushx, growx, wrap")
	 (.add chart-panel "push, grow")))

     (doto main-panel
       (.setPreferredSize (Dimension. 700 900))
       (.add left-panel "width 35%, pushy, growy")
       (.add right-panel "width 65%, pushy, growy")))
    main-panel))

(defn scroll-table-and-chart [table chart event]
  (dosync 
   (let [{:keys [percentage]} event]
     (swing 
       (suppress-events
	(table-controller/show-percentage table percentage)
	(chart-controller/show-percentage chart percentage))))))

(defn update-table [table event]
  (dosync
   (let [{:keys [row value]} event
	 table-widget (:widget @table)]
     (swing 
       (suppress-events
	(let [model (.getModel table-widget)]
	  (.setValueAt model value row 1)
	  (table-controller/show-cell table-widget row 1)))))))

(defn update-slider [slider event]
  (dosync 
   (let [{:keys [percentage]} event]
     (slider-controller/set-percentage slider percentage))))

(defn update-chart [chart event]
  (dosync 
   (let [{:keys [index value]} event]
     (swing
       (let [value (convert-to-double value)]
	 (chart-controller/set-chart-value chart 0 index value))))))

(defn init-save-button [editor]
  (create-save-button #(save editor)))

(defn init-zoom-button [editor]
  (create-zoom-button
   #(let [chart (:chart @editor)]
      (enable-zooming chart))))

(defn init-edit-button [editor]
  (create-edit-button
   #(let [chart (:chart @editor)]
      (enable-dragging chart))))

(defn init-reset-button [editor]
  (create-reset-button
   #(let [chart (:chart @editor)]
      (reset chart))))

(defn init-left-toolbar [editor]
  (let [toolbar (JToolBar. JToolBar/HORIZONTAL)]
    (doto toolbar
      (.setFloatable false))))

(defn init-right-toolbar [editor]
  (let [toolbar (JToolBar. JToolBar/HORIZONTAL)
	zoom-button (init-zoom-button editor)
	edit-button (init-edit-button editor)
	reset-button (init-reset-button editor)
	button-group (ButtonGroup.)]
    (.setSelected edit-button true)
    (doto button-group
      (.add zoom-button)
      (.add edit-button))
    (doto toolbar
      (.setFloatable false)
      (.add zoom-button)
      (.add edit-button)
      (.add reset-button))))

(defn open-curve-editor [lasfile curve]   
  (let [frame (init-frame lasfile curve)
	dirty-curve (lasso/deref-curve @curve)
	index (:index dirty-curve)
	editor (ref {})
	chart (chart-controller/init-chart curve dirty-curve)
	depth-data (:data index)
	slider-notches 200
	slider (slider-controller/init-slider slider-notches)
	table (table-controller/init-table index dirty-curve)
	editor-props (struct-map Editor
		       :frame frame
		       :lasfile lasfile
		       :index index 
		       :slider slider
		       :table table
		       :chart chart)
	left-tool-bar (init-left-toolbar editor)
	right-tool-bar (init-right-toolbar editor)
	table-panel (init-table-panel slider table)
	main-panel (init-main-panel chart table-panel left-tool-bar right-tool-bar)]

    (dosync (ref-set editor editor-props))
    (chart-controller/enable-dragging chart)

    (slider-controller/add-percentage-change-listener slider (partial scroll-table-and-chart table chart))
    (chart-controller/add-value-change-listener chart 
						(fn [event] 
						  (dosync
						   (let [{:keys [data-index value]} event
							 row (index-to-row (:widget @table) data-index)]
						     (update-table table {:row row :value value})))))
    (table-controller/add-value-change-listener table 
						(fn [event]
						  (dosync
						   (let [{:keys [row value]} event
							 index (row-to-index (:widget @table) row)]
						     (update-chart chart {:index index :value value})))))
    (chart-controller/add-percentage-change-listener chart (partial update-slider slider))

    (swing
      (table-controller/show-percentage table 0)
      (doto frame
	(.add main-panel)
	(.pack)
	(.setVisible true)))
    editor))