package com.intellipolis.traffic;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.geotools.api.data.*;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RoadGeometryService {
 private static final CoordinateReferenceSystem WGS84;
 static{try{WGS84=CRS.decode("EPSG:4326",true);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 private final Path raw;private final Path cache;private final Map<String,List<List<Double>>> memory=new HashMap<>();private final Set<String> attempted=new HashSet<>();
 public RoadGeometryService(@Value("${app.urban-data.raw-path:data/raw}")String rawPath){raw=Path.of(rawPath);cache=raw.resolveSibling("cache/traffic/node-link");}
 public synchronized Map<String,List<List<Double>>> coordinates(Set<String> ids){
  if(ids.isEmpty())return Map.of();
  Set<String> missing=new HashSet<>(ids);missing.removeAll(attempted);try{if(!missing.isEmpty())load(missing);}catch(Exception ignored){}finally{attempted.addAll(missing);}
  Map<String,List<List<Double>>> out=new HashMap<>();ids.forEach(id->{var value=memory.get(id);if(value!=null)out.put(id,value);});return out;
 }
 private void load(Set<String> ids)throws Exception{
  Path shp=cache.resolve("MOCT_LINK.shp");if(!Files.exists(shp))extract(raw.resolve("traffic/node-link/[2026-07-01]NODELINKDATA.zip"));
  DataStore store=DataStoreFinder.getDataStore(Map.of("url",shp.toUri().toURL()));if(store==null)return;
  try{var source=store.getFeatureSource(store.getTypeNames()[0]);var transform=CRS.findMathTransform(source.getSchema().getCoordinateReferenceSystem(),WGS84,true);
   try(var features=source.getFeatures().features()){while(features.hasNext()){SimpleFeature f=features.next();String id=linkId(f);if(id==null||!ids.contains(id)||memory.containsKey(id))continue;Geometry g=(Geometry)f.getDefaultGeometry();if(g==null||g.isEmpty())continue;Geometry wgs=JTS.transform(g,transform);memory.put(id,points(wgs));}}
  }finally{store.dispose();}
 }
 private String linkId(SimpleFeature f){for(String name:List.of("LINK_ID","link_id","LINKID","linkId")){Object v=f.getAttribute(name);if(v!=null)return v.toString();}return null;}
 private List<List<Double>> points(Geometry g){List<List<Double>> out=new ArrayList<>();for(Coordinate c:g.getCoordinates())out.add(List.of(c.x,c.y));return out;}
 private void extract(Path zip)throws IOException{
  if(!Files.exists(zip))return;Files.createDirectories(cache);
  try(ZipInputStream in=new ZipInputStream(Files.newInputStream(zip))){for(ZipEntry e;(e=in.getNextEntry())!=null;){String name=Path.of(e.getName()).getFileName().toString();if(e.isDirectory()||!name.toUpperCase(Locale.ROOT).startsWith("MOCT_LINK."))continue;Path target=cache.resolve(name);if(!Files.exists(target))Files.copy(in,target);}}
 }
}
