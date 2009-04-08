(ns file.view
  (:use gutil)
  (:import (javax.swing JMenu JFileChooser JPanel 
			JScrollPane JList DefaultListModel
			BorderFactory JTabbedPane)
	   (gui IconListCellRenderer)
	   (net.miginfocom.swing MigLayout)))

(defn create-curve-list []
  (let [jlist (new JList (new DefaultListModel))]
    (doto jlist
      (.setVisibleRowCount 0)
      (.setBorder (BorderFactory/createEmptyBorder))
      (.setCellRenderer (new IconListCellRenderer))
      (.setBackground (.getBackground (new JPanel)))
      (.setOpaque false))))

(defn create-curve-list-view [curve-list]
  (let [inner-panel (new JPanel (new MigLayout))
	pane (new JScrollPane inner-panel)
	outer-panel (new JPanel (new MigLayout))]
    (doto inner-panel
      (.add curve-list "pushx, growx, pushy, growy, wrap"))
    (doto outer-panel
      (.add pane "pushx, pushy, growx, growy, wrap"))))

(defn create-file-pane []
  (new JTabbedPane))