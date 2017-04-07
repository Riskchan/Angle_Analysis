import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import java.awt.event.*;
import java.util.*;

public class Angle_Analysis implements PlugIn, ActionListener {

	// Member variables

	Button m_bt_run;
	Button m_bt_set, m_bt_reset;
	TextField m_txt_low, m_txt_high;
	
	
	private double length_2d(double x1, double y1, double x2, double y2){
		return Math.sqrt((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2));
	}

	private double linear_val(double x, double y, double cx, double cy, double angle){
		return (y-cy)*Math.cos(angle) - (x-cx)*Math.sin(angle);
	}

	private double getIntersectionLength(double cx, double cy, double angle, Roi r){
		// Search through intersections
		ArrayList<Integer> idx = new ArrayList<Integer>();
		FloatPolygon pol = r.getFloatPolygon();
		int n = pol.npoints;
		for(int i=0; i<n-1; i++){
			double v1 = linear_val(pol.xpoints[i], pol.ypoints[i], cx, cy, angle);
			double v2 = linear_val(pol.xpoints[i+1], pol.ypoints[i+1], cx, cy, angle);
			// same side if v1*v2>0, otherwise opposite
			if(v1*v2 < 0){
				idx.add(i);
			}
		}

		// Finding desired direction and the farthest intersection
		ArrayList<Double> ix = new ArrayList<Double>();
		ArrayList<Double> iy = new ArrayList<Double>();
		for(int i=0; i<idx.size(); i++){
			double x1 = pol.xpoints[idx.get(i)];
			double y1 = pol.ypoints[idx.get(i)];

			double node_ang = Math.atan2(y1-cy, x1-cx);
			// atan2 returns -PI to PI -> 0 to 2*PI;
			if(node_ang < 0)
				node_ang += Math.PI*2;

			if(Math.abs(node_ang - angle) < Math.PI/2){
				double x2 = pol.xpoints[idx.get(i) + 1];
				double y2 = pol.ypoints[idx.get(i) + 1];

				// Solving
				// (x2-x1)(y-y1) = (y2-y1)(x-x1)
				// (y-cy)cos(angle) = (x-cx)sin(angle)
				double sin = Math.sin(angle);
				double cos = Math.cos(angle);
				double D = (y2-y1)*cos - (x2-x1)*sin;
				double a = cx*sin - cy*cos;
				double b = (y2-y1)*x1 - (x2-x1)*y1;

				// Calculating intersec and pushing into array list
				//IJ.log("ang=" + Math.atan2((b*sin - a*(y2-y1))/D-cy, (b*cos - a*(x2-x1))/D-cx));
				ix.add((b*cos - a*(x2-x1))/D);
				iy.add((b*sin - a*(y2-y1))/D);
				//IJ.log("(" + (b*cos - a*(x2-x1))/D + ", " + (b*sin - a*(y2-y1))/D + ")");
			}
		}

		// Finding farthest intersection
		double maxlen = 0;
		for(int i=0; i<ix.size(); i++){
			double len = length_2d(ix.get(i), iy.get(i), cx, cy);
			if(len>maxlen){
				maxlen = len;
			}
		}
		return maxlen;
	}

	private double[] getCenterOfMass(Roi roi){
		// Calculate center of mass
		Polygon poly = roi.getPolygon();
		double sumx = 0, sumy = 0;
		for(int i=0; i<poly.npoints; i++){
			sumx = sumx + poly.xpoints[i];
			sumy = sumy + poly.ypoints[i];
		}
		double[] com = new double[2];
		
		com[0] = sumx/poly.npoints;
		com[1] = sumy/poly.npoints;

		return com;
	}

	private void angle_analysis(int threshold_low, int threshold_high) {
		ImagePlus imp = IJ.getImage();
        if (null == imp) return;

		ImageProcessor ip = imp.getProcessor();

		// Find center of selection
		Roi roi_sel = imp.getRoi();
		if(null == roi_sel){
			IJ.showMessage("No area selected. ");
		}
        
		double[] com_sel = new double[2];
		com_sel = getCenterOfMass(roi_sel);

		// Set threshold and select
		Wand wand = new Wand(ip);
		wand.autoOutline((int)com_sel[0], (int)com_sel[1], threshold_low, threshold_high);
		PolygonRoi roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, Roi.FREELINE);
		imp.setRoi(roi);

		// Calculate COM of Wand
		double[] com = new double[2];
		com = getCenterOfMass(roi);

		int N = 360;
		IJ.log("\\Clear");
		for(int i=0; i<N; i++){
			double ang = 2*i*Math.PI/N;
			double len = getIntersectionLength(com[0], com[1], ang, roi);

			double vx = len * Math.cos(ang);
			double vy = len * Math.sin(ang);
			Line line = new Line(com[0], com[1], com[0]+vx, com[1]+vy);
			imp.setRoi(line);
			ProfilePlot prof = new ProfilePlot(imp);
			double max_prof = prof.getMax();
			IJ.log(String.valueOf(max_prof));
		}

		// Show polygon
		imp.setRoi(roi);
	}
	
    public void actionPerformed(ActionEvent e){
    	// Default values
		int low = Integer.parseInt(m_txt_low.getText());
		int high = Integer.parseInt(m_txt_high.getText());

		// IJ
		ImagePlus imp = IJ.getImage();
        if (null == imp) return;
		ImageProcessor ip = imp.getProcessor();		

		Button b = (Button)e.getSource();
    	if (b==null)
    		return;
    	else if (b==m_bt_run){
    		angle_analysis(low, high);
    	}else if (b==m_bt_set){
			ip.setThreshold(low, high, ImageProcessor.RED_LUT);
			imp.updateAndDraw();    		
    	}else if (b==m_bt_reset){
            //Execute when button is pressed 
        	ip.resetThreshold();
			imp.updateAndDraw();
    	}
	}
	
    public void run(String arg) {	
		
    	Frame frm = new Frame(new String("Angle analysis"));
    	frm.setSize(new Dimension(300,150));
    	frm.setLayout(new GridLayout(0, 1));
        frm.addWindowListener(new WindowAdapter() {
        	public void windowClosing(WindowEvent e) {
        		System.exit(0);
        	}
        });
        
		// Lower/Higher thresholds
		Panel p = new Panel();
		p.setLayout(new GridLayout(0, 2));
		Label l1 = new Label("Lower threshold level:");
		p.add(l1);
        m_txt_low = new TextField(2200);
        p.add(m_txt_low);

		Label l2 = new Label("Higher threshold level:");
		p.add(l2);
        m_txt_high = new TextField(10000);
        p.add(m_txt_high);
        frm.add(p);
        
		// Set/Reset threshold button
		p = new Panel();
		p.setLayout(new GridLayout(0, 2));

		m_bt_set = new Button("Set threshold");
		m_bt_set.addActionListener(this);
		p.add(m_bt_set);

		m_bt_reset = new Button("Reset threshold");
		m_bt_reset.addActionListener(this);
		p.add(m_bt_reset);
		frm.add(p);

		// Run button
		p = new Panel();
		p.setLayout(new GridLayout(1, 1));
		m_bt_run = new Button("Run");
		m_bt_run.addActionListener(this);
		p.add(m_bt_run);
		frm.add(p);

		// Show
		frm.show();

    }

}
