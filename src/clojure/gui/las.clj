(ns gui.las
  (:use util gui.curves gui.util gui.global gui.widgets)
  (:import (gui IconListCellRenderer ChartUtil)
	   (java.io File)
	   (javax.swing JList JFrame DefaultListModel ImageIcon JLabel
			JScrollPane JButton JWindow JPanel SwingUtilities
			JTabbedPane BorderFactory)
	   (javax.swing.border BevelBorder)
	   (javax.imageio ImageIO)
	   (net.miginfocom.swing MigLayout)
	   (java.awt Dimension Image Color)
	   (java.awt.event MouseMotionAdapter MouseAdapter MouseEvent)))

(defn- create-curve-panel []
  (let [panel (new JPanel (new MigLayout))]
    (doto panel
      (.setBorder (BorderFactory/createEtchedBorder)))
    panel))

(def las-views (agent {}))
(def current-las-view (agent (create-curve-panel)))
(def las-panel 
     (let [panel (create-titled-panel "Curves")]
       (doto panel
	 (.add @current-las-view "pushy, growy, pushx, growx")
	 (.revalidate))
       panel))

(def las-curves (agent {}))

(defn add-curve [lasfile curve]
  (let [icon (ChartUtil/curveToIcon curve)]
    (send las-curves 
	  (fn [lcs]
	    (let [[jlist curves] (get lcs lasfile)]
	      (swing (.addElement (.getModel jlist) icon))
	      (assoc lcs lasfile [jlist (conj curves curve)])
	      )))))

(defn remove-curve [lasfile curve]
  (send las-curves
	(fn [lcs]
	  (let [[jlist curves] (get lcs lasfile)
		index (index-of curve curves)]
	    (swing (.removeElementAt (.getModel jlist) index))
	    (assoc lcs lasfile [jlist (remove #(= curve %) curves)])
	    ))))		   

(defn- get-selected-curves [jlist curves]
  (let [selected (map #(.getText %) (.getSelectedValues jlist))]
    (filter (fn [curve]
	      (let [name (.getMnemonic curve)]
		(some #(= name %) selected)))
	    curves)))

(defn- open-curves-context-menu [lasfile jlist event]
  (let [curves (.getCurves lasfile)
	[c x y] [(.getComponent event) (.getX event) (.getY event)]
	scurves (get-selected-curves jlist curves)]
    (context-menu [c x y]
      ["Copy" (fn [e] (send copied-curves (fn [x] scurves)))]
      ["Paste" (fn [e] 
		 (doseq [curve @copied-curves]
		   (add-curve lasfile curve)))])))

(defn- create-curve-list [lasfile]
  (let [curves (.getCurves lasfile)
	jlist (create-jlist)]
    (send las-curves
	  (fn [lcs]
	    (long-task
	     (doseq [curve curves]	       
	       (add-curve lasfile curve)))
	    (assoc lcs lasfile [jlist []])))
    
    (swing 
     (doto jlist
       (.setFixedCellHeight 80)
       (.setOpaque false))
     
     (on-click jlist
       (fn [e]
	 (cond
	  (and (= MouseEvent/BUTTON1 (.getButton e))
	       (= 2 (.getClickCount e)))
	  (doseq [sc (get-selected-curves jlist curves)]
	    (swing (open-curve-editor sc)))
	  
	  (= MouseEvent/BUTTON3 (.getButton e))
	  (swing (open-curves-context-menu lasfile jlist e))))))
    jlist))

(defn create-las-view [lasfile]
  (let [curve-list (create-curve-list lasfile)
	inner-panel (create-inner-panel)
	pane (new JScrollPane inner-panel)
	outer-panel (create-curve-panel)]
    
    (doto inner-panel
      (.add curve-list "pushx, growx, pushy, growy, wrap"))

    (doto outer-panel 
      (.add pane "pushx, pushy, growx, growy, wrap")
      (.setPreferredSize (new Dimension 400 700)))
    outer-panel))

(defn set-las-view [new-view]
  (send current-las-view
	(fn [old-view]
	  (swing 
	   (doto las-panel
	     (.remove old-view)
	     (.add new-view)
	     (.revalidate))
	   (doto new-view
	     (.repaint)
	     (.revalidate)))
	  new-view)))

(defn open-las-view [lasfile]
  (send las-views 
	(fn [views]
	  (let [existing-view (get views lasfile)]
	    (if existing-view
	      (do 
		(set-las-view existing-view)
		views)
	      (let [view (create-las-view lasfile)]
		(do
		  (set-las-view view)
		  (assoc views lasfile view))))))))