package lcpc_son;

import java.util.ArrayList;
import java.util.HashSet;

import org.grap.utilities.EnvelopeUtil;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.operation.predicate.RectangleIntersects;

/**
 * GridIndex is a class to speed up the query of a geometry collection inside a region envelope
 * @author N.Fortin J.Picaut (IFSTTAR 2011)
 */
public class GridIndex {
	private int[] grid=null;
	private int nbI=0;
	private int nbJ=0;
	private double cellSizeI;
	private double cellSizeJ;
	private ArrayList<ArrayList<Integer>> gridContent=new ArrayList<ArrayList<Integer>>();
	private Envelope mainEnv;
	
	
	
	public GridIndex(final Envelope gridEnv,int xsubdiv,int ysubdiv) {
		super();
		grid=new int[xsubdiv*ysubdiv];
		for(int i=0;i<grid.length;i++)
			grid[i]=-1;
		mainEnv=gridEnv;
		nbJ=xsubdiv;
		nbI=ysubdiv;
		cellSizeI=mainEnv.getHeight()/nbI;
		cellSizeJ=mainEnv.getWidth()/nbJ;
	}
	
	private Envelope GetCellEnv(int i,int j)
	{
		final double minx=mainEnv.getMinX()+cellSizeJ*j;
		final double miny=mainEnv.getMinY()+cellSizeI*i;
		return new Envelope(minx,minx+cellSizeJ,miny,miny+cellSizeI);
	}
	private void AddItem(int i,int j,Integer content)
	{
		int idcontent=grid[j+i*nbJ];
		if(idcontent==-1)
		{
			idcontent=gridContent.size();
			gridContent.add(new ArrayList<Integer>());
			grid[j+i*nbJ]=idcontent;
		}
		gridContent.get(idcontent).add(content);
	}
	private int[] GetRange(Envelope geoEnv)
	{
		//Compute index intervals from envelopes 
		Coordinate mainCenter=mainEnv.centre();
		Coordinate tmpvec=new Coordinate((geoEnv.getMinX()-mainCenter.x)/cellSizeJ,(geoEnv.getMinY()-mainCenter.y)/cellSizeI);
		int halfCellCountI=nbI/2;
		int halfCellCountJ=nbJ/2;
		int minI=(int)(Math.floor(tmpvec.y))+halfCellCountI;
		int minJ=(int)(Math.floor(tmpvec.x))+halfCellCountJ;
		tmpvec=new Coordinate((geoEnv.getMaxX()-mainCenter.x)/cellSizeJ,(geoEnv.getMaxY()-mainCenter.y)/cellSizeI);
		int maxI=(int)(Math.ceil(tmpvec.y))+halfCellCountI;
		int maxJ=(int)(Math.ceil(tmpvec.x))+halfCellCountJ;
		if(minI==maxI)
			maxI+=1;
		if(minJ==maxJ)
			maxJ+=1;
		if(minI<0)
			minI=0;
		if(minJ<0)
			minJ=0;
		if(maxI>nbI)
			maxI=nbI;
		if(maxJ>nbJ)
			maxJ=nbJ;
		int[] range={minI,maxI,minJ,maxJ};
		return range;
	}
	public void AppendGeometry(Geometry newGeom,final Integer externalId)
	{
		//Compute index intervals from envelopes 
	
		int[] ranges= GetRange(newGeom.getEnvelopeInternal());
		int minI=ranges[0],maxI=ranges[1],minJ=ranges[2],maxJ=ranges[3];
		GeometryFactory factory=new GeometryFactory();
		for(int i=minI;i<maxI;i++)
		{
			for(int j=minJ;j<maxJ;j++)
			{
				Envelope cellEnv=GetCellEnv(i, j);
				
				Polygon square=factory.createPolygon((LinearRing) EnvelopeUtil.toGeometry(cellEnv), null);
				RectangleIntersects inter=new RectangleIntersects(square);
				if(inter.intersects(newGeom))
				{
					AddItem(i, j, externalId);
				}
			}
		}
	}
	public ArrayList<Integer> query(Envelope queryEnv)
	{
		int[] ranges= GetRange(queryEnv);
		int minI=ranges[0],maxI=ranges[1],minJ=ranges[2],maxJ=ranges[3];
		HashSet<Integer> querySet=new HashSet<Integer>();
		for(int i=minI;i<maxI;i++)
		{
			for(int j=minJ;j<maxJ;j++)
			{
				int contentId=grid[j+i*nbJ];
				if(contentId!=-1)
				{
					querySet.addAll(gridContent.get(contentId));
				}
			}
		}
		return new ArrayList<Integer>(querySet);
	}

}
