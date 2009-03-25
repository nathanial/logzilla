package gui

import java.awt._
import java.awt.event.{MouseEvent}
import java.awt.image.{BufferedImage}
import java.awt.geom.{Rectangle2D,Point2D}
import javax.swing._
import javax.swing.border._
import java.util.{List,LinkedList}
import scala.collection.jcl.Conversions._
import org.jfree.chart.{ChartFactory, ChartPanel, JFreeChart, ChartRenderingInfo}
import org.jfree.chart.plot.{PlotOrientation, XYPlot}
import org.jfree.chart.entity.{ChartEntity,XYItemEntity}
import org.jfree.chart.{ChartMouseEvent, ChartMouseListener}
import org.jfree.data.xy.{AbstractXYDataset, XYDataset, 
			  XYSeries, XYSeriesCollection}
import org.jfree.ui.RectangleEdge

import org.jdesktop.swingx.graphics.ShadowRenderer
import java.util.concurrent.locks.{ReadWriteLock,ReentrantReadWriteLock}

class CustomJTable extends JTable {
  def showCell(row:Int, col:Int){
    val rect = getCellRect(row, col, true)
    scrollRectToVisible(rect)
  }

  def showAtPercentage(n:Double){
    if(n > 1 || n < 0)
      throw new RuntimeException("invalid n (must be from 0.0 to 1.0) : " + n) 
    val rows = getRowCount - 1
    val row = (n * rows).intValue
    showCell(row, 0)
  }
}

class CustomChartPanel(curve: Curve, chart:JFreeChart) 
extends ChartPanel(chart, false, false, false, false, false) {
  val chartMouseListeners = new LinkedList[ChartMouseListener]

  def java2DToValue(x:Double) = {
    val xaxis = chart.getPlot.asInstanceOf[XYPlot].getRangeAxis //reversed, remember?
    val value = xaxis.java2DToValue(x, 
				    getScreenDataArea,
				    RectangleEdge.TOP) //dunno why this has to be top
    value
  }

  lazy val curveXRange = {
    val data = curve.getLasData
    val cmax = data.reduceLeft(Math.max)
    val cmin = data.reduceLeft(Math.min)
    cmax - cmin
  }

  def getCurve:Curve = curve

  override def mouseClicked(event:MouseEvent) {
    val insets = getInsets()
    var x = ((event.getX() - insets.left) / getScaleX).asInstanceOf[Int]
    var y = ((event.getY() - insets.top) / getScaleY).asInstanceOf[Int]
    
    setAnchor(new Point2D.Double(x, y))
    if (getChart == null) return
    getChart.setNotify(true)  // force a redraw
    // new entity code...
    if(chartMouseListeners.length == 0) return
    
    var entity:ChartEntity = null
    if (getChartRenderingInfo != null) {
      val entities = getChartRenderingInfo.getEntityCollection()
      if (entities != null) {
	//bounds are reversed because graph is vertical
	val picks = new LinkedList[ChartEntity]
	for(entity <- entities.getEntities.toArray){
	  val bounds = entity.asInstanceOf[ChartEntity].getArea.getBounds
	  if(Math.abs(x - bounds.y) < 10 && Math.abs(y - bounds.x) < 10){
	    picks.add(entity.asInstanceOf[ChartEntity])
	  }
	}
	if(picks.length > 0){
	  var chosen = picks.first
	  def delta(p:ChartEntity) = {
	    val bounds = p.getArea.getBounds
	    Math.sqrt(Math.pow(Math.abs(x - bounds.y),2) + 
		      Math.pow(Math.abs(y - bounds.x),2))
	  }
	  for(pick <- picks){
	    if(delta(pick) < delta(chosen)){
	      chosen = pick
	    }
	  }
	  entity = chosen
	}
      }
    }
    val chartEvent = new ChartMouseEvent(getChart(), event, entity)
    for(listener <- chartMouseListeners.reverse){
      listener.asInstanceOf[ChartMouseListener].chartMouseClicked(chartEvent)
    }
  }
  
  override def addChartMouseListener(listener:ChartMouseListener){
    chartMouseListeners += listener
    super.addChartMouseListener(listener)
  }

  override def removeChartMouseListener(listener:ChartMouseListener){
    chartMouseListeners -= listener
    super.addChartMouseListener(listener)
  }

}

class IconListCellRenderer extends JLabel with ListCellRenderer {

  override def getListCellRendererComponent(list:JList, value:Object, index:int, isSelected:boolean, hasFocus:boolean) = {
    val jlabel = value.asInstanceOf[JLabel]
    setOpaque(true)
    setText(jlabel.getText)
    setIcon(jlabel.getIcon)
    setComponentOrientation(list.getComponentOrientation)
    if(isSelected){
      setBackground(list.getSelectionBackground)
      setForeground(list.getSelectionForeground)
    }
    else{
      setBackground(list.getBackground)
      setForeground(list.getForeground)
    }
    if(hasFocus){
      setBorder(UIManager.getBorder("List.focusCellHighlightBorder"))
    }
    else {
      setBorder(new EmptyBorder(1,1,1,1))
    }
    this
  }

  override protected def paintComponent(g: Graphics){
    import RenderingHints._
    val g2 = g.asInstanceOf[Graphics2D]
    val hint = g2.setRenderingHint _
    hint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON)
    hint(KEY_TEXT_ANTIALIASING, VALUE_TEXT_ANTIALIAS_ON)
    hint(KEY_RENDERING, VALUE_RENDER_QUALITY)
    super.paintComponent(g2)
  }

}


object ChartUtil {
  def time[A](msg: String)(f: => A):A = {
    val start = System.currentTimeMillis
    val result = f
    val end = System.currentTimeMillis
    println(msg + " " + (end - start))
    return result
  }

  def createChart(dataset:XYSeriesCollection, curve:Curve):JFreeChart = {
    val cname = curve.getMnemonic
    val iname = curve.getIndex.getMnemonic
    val chart = ChartFactory.createXYLineChart(
      cname + " Chart",
      iname, cname,
      dataset, PlotOrientation.HORIZONTAL,
      false, false, false)
    val plot = chart.getPlot.asInstanceOf[XYPlot]
    val renderer = plot.getRenderer
    renderer.setBasePaint(Color.blue)
    renderer.setSeriesPaint(0, Color.blue)
    plot.setBackgroundPaint(Color.white)
    chart
  }

  def createChart(curve:Curve):JFreeChart = {
    createChart(createDataset(curve), curve)
  }

  def createDataset(curve: Curve) = {
    val series = new XYSeries("Series")
    val ds = new XYSeriesCollection
    val index = curve.getIndex
    val cdata = curve.getLasData
    val idata = index.getLasData
    
    for(i <- 0 until idata.size){
      series.add(idata(i), 
		 cdata(i))
    }

    ds.addSeries(series)
    ds
  }

  def curveToImage(curve: Curve):BufferedImage = {
    createChart(curve).createBufferedImage(400,700)
  }

  def curveToIcon(curve: Curve):JLabel = {
    val chart = createChart(curve)
    val image = new BufferedImage(400, 700, BufferedImage.TYPE_INT_ARGB)

    var graphics = image.createGraphics
    chart.draw(graphics, new Rectangle2D.Double(0,0,400,700), null, null)    
    graphics.dispose()    

    val finalImage = renderShadow(fastScale(image,64,64))

    val icon = new ImageIcon(finalImage)
    val name = curve.getMnemonic
    new JLabel(name, icon, SwingConstants.LEFT)
  }
  
  def renderShadow(image:BufferedImage) = {
    val shadowRenderer = new ShadowRenderer()
    val shadow = shadowRenderer.createShadow(image)
    stackImages(image, shadow)
  }

  def stackImages(top:BufferedImage, bottom:BufferedImage) = {
    val graphics = bottom.createGraphics
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f))
    graphics.drawImage(top, 0, 0, null)
    graphics.dispose()
    bottom
  }

  def fastScale(img:BufferedImage, targetWidth: Int, targetHeight: Int) = {
    var itype:Int = BufferedImage.TYPE_INT_ARGB

    var ret = img
    var scratchImage:BufferedImage = null
    var g2:Graphics2D = null
    var (w,h) = (0,0)
      var prevW = ret.getWidth
    var prevH = ret.getHeight
    w = img.getWidth
    h = img.getHeight
    
    do {
      if(w > targetWidth){
	w /= 2
	if(w < targetWidth){
	  w = targetWidth
	}
      }
      
      if(h > targetHeight){
	h /= 2
	if(h < targetHeight){
	  h = targetHeight
	}
      }

      if(scratchImage == null){
	scratchImage = new BufferedImage(w,h,itype)
	g2 = scratchImage.createGraphics
      }

      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			  RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      g2.drawImage(ret, 0, 0, w, h, 0, 0, prevW, prevH, null)
      prevW = w
      prevH = h
      
      ret = scratchImage
    } while(w != targetWidth || h != targetHeight)

    if(g2 != null){
      g2.dispose()
    }

    if(targetWidth != ret.getWidth || targetHeight != ret.getHeight) {
      scratchImage = new BufferedImage(targetWidth,
				       targetHeight, itype)
      g2 = scratchImage.createGraphics
      g2.drawImage(ret, 0, 0, null)
      g2.dispose()
      ret = scratchImage
    }
    
    ret
  }

}
