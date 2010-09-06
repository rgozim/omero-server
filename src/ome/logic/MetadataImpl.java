/*
 *  $Id$
 *
 *------------------------------------------------------------------------------
 *  Copyright (C) 2006-2009 University of Dundee. All rights reserved.
 *
 *
 * 	This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 */
package ome.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;

import ome.annotations.NotNull;
import ome.annotations.RolesAllowed;
import ome.annotations.Validate;
import ome.api.IContainer;
import ome.api.IMetadata;
import ome.api.ServiceInterface;
import ome.conditions.ApiUsageException;
import ome.model.IAnnotated;
import ome.model.IObject;
import ome.model.annotations.AnnotationAnnotationLink;
import ome.model.annotations.DatasetAnnotationLink;
import ome.model.annotations.FileAnnotation;
import ome.model.annotations.ImageAnnotationLink;
import ome.model.annotations.PlateAnnotationLink;
import ome.model.annotations.ProjectAnnotationLink;
import ome.model.annotations.ScreenAnnotationLink;
import ome.model.annotations.WellSampleAnnotationLink;
import ome.model.acquisition.Arc;
import ome.model.acquisition.Filament;
import ome.model.acquisition.Instrument;
import ome.model.acquisition.Laser;
import ome.model.acquisition.LightEmittingDiode;
import ome.model.acquisition.LightSettings;
import ome.model.acquisition.LightSource;
import ome.model.annotations.Annotation;
import ome.model.containers.Project;
import ome.model.core.LogicalChannel;
import ome.model.core.OriginalFile;
import ome.model.screen.Screen;
import ome.parameters.Parameters;
import ome.services.query.PojosFindAnnotationsQueryDefinition;
import ome.services.query.Query;


/** 
 * Implement the {@link IMetadata} I/F.
 *
 * @author  Jean-Marie Burel &nbsp;&nbsp;&nbsp;&nbsp;
 * <a href="mailto:j.burel@dundee.ac.uk">j.burel@dundee.ac.uk</a>
 * @author Donald MacDonald &nbsp;&nbsp;&nbsp;&nbsp;
 * <a href="mailto:donald@lifesci.dundee.ac.uk">donald@lifesci.dundee.ac.uk</a>
 * @version 3.0
 * <small>
 * (<b>Internal version:</b> $Revision: $Date: $)
 * </small>
 * @since 3.0-Beta4
 */
public class MetadataImpl 
	extends AbstractLevel2Service 
	implements IMetadata
{

	/** Query to load the original file related to a file annotation. */
	private final String LOAD_ORIGINAL_FILE = 
		"select p from OriginalFile as p where p.id = :id";
	
	/** Identifies the file annotation class. */
	private final String FILE_TYPE = "ome.model.annotations.FileAnnotation";
	
	/** Identifies the tag annotation class. */
	private final String TAG_TYPE = "ome.model.annotations.TagAnnotation";
	
	/** Reference to the {@link IContainer} service. */
	private IContainer iContainer;

	/**
     * Builds the <code>StringBuilder</code> corresponding to the passed 
     * light source.
     * 
     * @param src The light source to handle.
     * @return See above.
     */
    private StringBuilder createLightQuery(LightSource src)
    {
    	if (src == null) return null;
    	StringBuilder sb = new StringBuilder();
    	if (src instanceof Laser) {
			sb.append("select l from Laser as l ");
			sb.append("left outer join fetch l.type as type ");
			sb.append("left outer join fetch l.laserMedium as " +
					"medium ");
			sb.append("left outer join fetch l.pulse as pulse ");
	        sb.append("where l.instrument.id = :instrumentId");
		} else if (src instanceof Filament) {
			sb.append("select l from Filament as l ");
			sb.append("left outer join fetch l.type as type ");
	        sb.append("where l.instrument.id = :instrumentId");
		} else if (src instanceof Arc) {
			sb.append("select l from Arc as l ");
			sb.append("left outer join fetch l.type as type ");
	        sb.append("where l.instrument.id = :instrumentId");
		}
    	return sb;
    }
    
	/**
	 * Retrieves the annotation of the given type.
	 * 
	 * @param <A>	  The annotation returned.
	 * @param type    The type of annotation to retrieve.
	 * @param include The collection of name spaces to include.
	 * @param exclude The collection of name spaces to exclude.
	 * @param options The options if any.
	 * @return See above.
	 */
	private <A extends Annotation> List<A> getAnnotation(@NotNull Class type, 
    		Set<String> include, Set<String> exclude, Parameters options)
    {
    	StringBuilder sb = new StringBuilder();
    	sb.append("select ann from Annotation as ann ");
    	sb.append("left outer join fetch ann.details.creationEvent ");
    	sb.append("left outer join fetch ann.details.owner ");
    	sb.append("where ann member of "+type.getName());
    	
    	Parameters param = new Parameters();
    	Parameters po = new Parameters(options);
    	if (po.getExperimenter() != null) {
    		sb.append(" and ann.details.owner.id = :userId");
    		param.addLong("userId", po.getExperimenter());
    	} 

    	if (include != null && include.size() > 0) {
    		sb.append(" and ann.ns is not null and ann.ns in (:include)");
    		param.addSet("include", include);
    	}
    	if (exclude != null && exclude.size() > 0) {
    		sb.append(" and (ann.ns is null or ann.ns not in (:exclude))");
    		param.addSet("exclude", exclude);
    	}
    	return iQuery.findAllByQuery(sb.toString(), param);
    }
	
	/**
	 * Returns the Interface implemented by this class.
	 * 
	 * @return See above.
	 */
    public final Class<? extends ServiceInterface> getServiceInterface() {
        return IMetadata.class;
    }
    
    /**
     * IContainer bean injector. For use during configuration. Can only be called 
     * once.
     * @param iContainer The value to set.
     */
    public final void setIContainer(IContainer iContainer)
    {
        getBeanHelper().throwIfAlreadySet(this.iContainer, iContainer);
        this.iContainer = iContainer;
    }
    
    /**
     * Counts the number of <code>IObject</code>s (Project, Dataset or Image)
     * linked to the specified tag.
     * 
     * @param tagID The id of the tag.
     * @return See above.
     */
    private long countTaggedObjects(long tagID)
    {
    	Parameters param = new Parameters();
    	param.addId(tagID);
    	StringBuilder sb = new StringBuilder();
    	sb.append("select img from Image as img ");
		sb.append("left outer join fetch img.annotationLinks ail ");
		sb.append("where ail.child.id = :id");
		List l = iQuery.findAllByQuery(sb.toString(), param);
		long n = 0; 
		if (l != null) n += l.size();
		sb = new StringBuilder();
    	sb.append("select d from Dataset as d ");
		sb.append("left outer join fetch d.annotationLinks ail ");
		sb.append("where ail.child.id = :id");
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null) n += l.size();
		sb = new StringBuilder();
    	sb.append("select p from Project as p ");
		sb.append("left outer join fetch p.annotationLinks ail ");
		sb.append("where ail.child.id = :id");
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null) n += l.size();
		sb = new StringBuilder();
    	sb.append("select p from Screen as p ");
		sb.append("left outer join fetch p.annotationLinks ail ");
		sb.append("where ail.child.id = :id");
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null) n += l.size();
		sb = new StringBuilder();
    	sb.append("select p from Plate as p ");
		sb.append("left outer join fetch p.annotationLinks ail ");
		sb.append("where ail.child.id = :id");
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null) n += l.size();
		return n;
    }
    
    /**
     * Loads the objects linked to a tag.
     * 
     * @param id 		The id of the tag.
     * @param options 	The options.
     * @return See above.
     */
    private Set<IObject> loadObjects(long id, Parameters options)
    {
    	Parameters param = new Parameters();
    	param.addId(id);
    	StringBuilder sb = new StringBuilder();
    	Set result = new HashSet();    	//images linked to it.
    	
    	sb.append("select img from Image as img ");
		sb.append("left outer join fetch "
				+ "img.annotationLinksCountPerOwner img_a_c ");
		sb.append("left outer join fetch img.annotationLinks ail ");
		sb.append("left outer join fetch ail.child child ");
		sb.append("left outer join fetch ail.parent parent ");
		sb.append("left outer join fetch child.details.owner ownerChild ");
		sb.append("left outer join fetch parent.details.owner ownerParent ");
		sb.append("left outer join fetch img.pixels as pix ");
		sb.append("left outer join fetch pix.pixelsType as pt ");
		sb.append("where child.id = :id");
		List l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null) result.addAll(l);
		sb = new StringBuilder();
		sb.append("select d from Dataset as d ");
		sb.append("left outer join fetch "
				+ "d.annotationLinksCountPerOwner d_a_c ");
		sb.append("left outer join fetch d.annotationLinks ail ");
		sb.append("left outer join fetch ail.child child ");
		sb.append("left outer join fetch ail.parent parent ");
		sb.append("left outer join fetch child.details.owner ownerChild ");
		sb.append("left outer join fetch parent.details.owner ownerParent ");
		sb.append("where child.id = :id");
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null) result.addAll(l);
		
		sb = new StringBuilder();
		sb.append("select pl from Plate as pl ");
		sb.append("left outer join fetch "
				+ "pl.annotationLinksCountPerOwner pl_a_c ");
		sb.append("left outer join fetch pl.annotationLinks ail ");
		sb.append("left outer join fetch ail.child child ");
		sb.append("left outer join fetch ail.parent parent ");
		sb.append("where child.id = :id");
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null) result.addAll(l);
		
		
		sb = new StringBuilder();
		sb.append("select p from Project as p ");
		sb.append("left outer join fetch "
				+ "p.annotationLinksCountPerOwner p_a_c ");
		sb.append("left outer join fetch p.annotationLinks ail ");
		sb.append("left outer join fetch ail.child child ");
		sb.append("left outer join fetch ail.parent parent ");
		sb.append("left outer join fetch child.details.owner ownerChild ");
		sb.append("left outer join fetch parent.details.owner ownerParent ");
		sb.append("where child.id = :id");
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null && l.size() > 0) {
			Set<Long> ids = new HashSet<Long>();
			Iterator i = l.iterator();
			while (i.hasNext()) {
				ids.add(((IObject) i.next()).getId());
			}
			Parameters po = new Parameters(options);
			po.noLeaves();
			po.noOrphan();
			Set p = iContainer.loadContainerHierarchy(Project.class, ids, po);
			result.addAll(p);
		}
		
		sb = new StringBuilder();
		sb.append("select s from Screen as s ");
		sb.append("left outer join fetch "
				+ "s.annotationLinksCountPerOwner s_a_c ");
		sb.append("left outer join fetch s.annotationLinks ail ");
		sb.append("left outer join fetch ail.child child ");
		sb.append("left outer join fetch ail.parent parent ");
		sb.append("left outer join fetch child.details.owner ownerChild ");
		sb.append("left outer join fetch parent.details.owner ownerParent ");
		sb.append("where child.id = :id");
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null && l.size() > 0) {
			Set<Long> ids = new HashSet<Long>();
			Iterator i = l.iterator();
			while (i.hasNext()) {
				ids.add(((IObject) i.next()).getId());
			}
			Parameters po = new Parameters(options);
			po.noLeaves();
			po.noOrphan();
			Set p = iContainer.loadContainerHierarchy(Screen.class, ids, po);
			result.addAll(p);
		}
    	return result;
    }
    
    /**
     * Implemented as specified by the {@link IMetadata} I/F
     * @see IMetadata#loadInstrument(Long)
     */
    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Set<IObject> loadInstrument(long id)
    {
    	Set<IObject> results = new HashSet<IObject>();
    	StringBuilder sb = new StringBuilder();
    	Parameters params = new Parameters(); 
    	sb.append("select inst from Instrument as inst ");
    	sb.append("left outer join fetch inst.microscope as m ");
    	sb.append("left outer join fetch m.type as mt ");
    	sb.append("where inst.id = :id");
    	Instrument value = iQuery.findByQuery(sb.toString(), 
    			params.addId(id));
    	if (value == null) return results;
    	results.add(value);
    	//detectors
    	sb = new StringBuilder();
    	params = new Parameters(); 
    	params.addLong("instrumentId", id);
    	sb.append("select d from Detector as d ");
    	sb.append("left outer join fetch d.type as dt ");
    	sb.append("where d.instrument.id = :instrumentId");
    	
    	List<IObject> list = iQuery.findAllByQuery(sb.toString(), params);
    	if (list != null) results.addAll(list);
    	
    	//filters
    	sb = new StringBuilder();
    	sb.append("select f from Filter as f ");
    	sb.append("left outer join fetch f.type as ft ");
    	sb.append("left outer join fetch f.transmittanceRange as trans ");
    	sb.append("where f.instrument.id = :instrumentId");
    	list = iQuery.findAllByQuery(sb.toString(), params);
    	if (list != null) results.addAll(list);
    	
    	//filter set
    	sb = new StringBuilder();
    	sb.append("select f from FilterSet as f ");
    	sb.append("left outer join fetch f.dichroic as dichroic ");
    	sb.append("where f.instrument.id = :instrumentId");
    	list = iQuery.findAllByQuery(sb.toString(), params);
    	if (list != null) results.addAll(list);
    	
    	//dichroics
    	sb = new StringBuilder();
    	sb.append("select d from Dichroic as d ");
    	sb.append("where d.instrument.id = :instrumentId");
    	list = iQuery.findAllByQuery(sb.toString(), params);
    	if (list != null) results.addAll(list);
    	
    	//objectives
    	sb = new StringBuilder();
    	sb.append("select o from Objective as o ");
    	sb.append("left outer join fetch o.immersion as oi ");
    	sb.append("left outer join fetch o.correction as oc ");
    	sb.append("where o.instrument.id = :instrumentId");
    	list = iQuery.findAllByQuery(sb.toString(), params);
    	if (list != null) results.addAll(list);
    	
    	//light sources
    	sb = new StringBuilder();
    	sb.append("select light from LightSource as light ");
    	//sb.append("left outer join fetch light.type as t ");
    	sb.append("where light.instrument.id = :instrumentId");
    	list = iQuery.findAllByQuery(sb.toString(), params);
    	//if (list != null) results.addAll(list);
    	if (list != null) {
    		List<IObject> objects;
    		Iterator i = list.iterator();
    		LightSource src;
    		List<String> names = new ArrayList<String>();
    		String name;
    		while (i.hasNext()) {
            	src = (LightSource) i.next();
            	if (src instanceof LightEmittingDiode) {
            		results.add(src);
            	} else {
            		name = src.getClass().getName();
            		if (!names.contains(name)) {
            			names.add(name);
            			sb = createLightQuery(src);
            			objects = iQuery.findAllByQuery(sb.toString(), params);
            			if (objects != null && objects.size() > 0)
            				results.addAll(objects);
            				
            		}
            	}
    		}
    	}
    	return results;
    }
    
    /**
     * Implemented as specified by the {@link IMetadata} I/F
     * @see IMetadata#loadChannelAcquisitionData(Set)
     */
    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Set loadChannelAcquisitionData(@NotNull 
			@Validate(Long.class) Set<Long> ids)
    {
    	StringBuilder sb = new StringBuilder();
    	sb.append("select channel from LogicalChannel as channel ");
    	sb.append("left outer join fetch channel.mode as mode ");
        sb.append("left outer join fetch channel.illumination as illumination ");
        sb.append("left outer join fetch channel.contrastMethod as cm ");
		sb.append("left outer join fetch channel.detectorSettings as ds ");
        sb.append("left outer join fetch channel.lightSourceSettings as lss ");
        sb.append("left outer join fetch channel.filterSet as filter ");
        sb.append("left outer join fetch filter.dichroic as dichroic ");
        
        //emission filters
        sb.append("left outer join fetch filter.emissionFilterLink as efl ");
        sb.append("left outer join fetch efl.child as ef ");
        sb.append("left outer join fetch ef.transmittanceRange as efTrans ");
        sb.append("left outer join fetch ef.type as type1 ");
        
        //excitation filters
        sb.append("left outer join fetch filter.excitationFilterLink as exfl ");
        sb.append("left outer join fetch exfl.child as exf ");
        sb.append("left outer join fetch exf.transmittanceRange as exfTrans ");
        sb.append("left outer join fetch exf.type as type2 ");
        
        sb.append("left outer join fetch channel.lightPath as lp ");
        sb.append("left outer join fetch lp.dichroic as dichroic ");
        
      //emission filters
        sb.append("left outer join fetch lp.emissionFilterLink as efLpl ");
        sb.append("left outer join fetch efLpl.child as efLp ");
        sb.append("left outer join fetch efLp.transmittanceRange as efLpTrans ");
        sb.append("left outer join fetch efLp.type as type3 ");
        
        //excitation filters
        sb.append("left outer join fetch lp.excitationFilterLink as exfLpl ");
        sb.append("left outer join fetch exfLpl.child as exfLp ");
        sb.append("left outer join fetch exfLp.transmittanceRange as exfLpTrans ");
        sb.append("left outer join fetch exfLp.type as type4 ");

        sb.append("left outer join fetch ds.detector as detector ");
        sb.append("left outer join fetch detector.type as dt ");
        sb.append("left outer join fetch ds.binning as binning ");
        sb.append("left outer join fetch lss.lightSource as light ");
        sb.append("left outer join fetch light.instrument as instrument ");
        sb.append("where channel.id in (:ids)");
        List<LogicalChannel> list = iQuery.findAllByQuery(sb.toString(), 
        		new Parameters().addIds(ids));
        Iterator<LogicalChannel> i = list.iterator();
        LogicalChannel channel;
        LightSettings light;
        LightSource src;
        IObject object;
        Parameters params; 
    	
        while (i.hasNext()) {
        	channel = i.next();
			light = channel.getLightSourceSettings();
			if (light != null) {
				src = light.getLightSource();
				if (src instanceof LightEmittingDiode) {
					light.setLightSource(src);
				} else {
					sb = createLightQuery(src);
					if (sb != null) {
						params = new Parameters(); 
						params.addLong("instrumentId", 
								src.getInstrument().getId());
						params.addId(src.getId());
						sb.append(" and l.id = :id");
						object = iQuery.findByQuery(sb.toString(), params);
						light.setLightSource((LightSource) object);
					}
				}
			}
		}
    	return new HashSet<LogicalChannel>(list);
    }

    /**
     * Implemented as specified by the {@link IMetadata} I/F
     * @see IMetadata#loadAnnotations(Class, Set, Set, Set)
     */
    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public <T extends IObject, A extends Annotation> 
    	Map<Long, Set<A>> loadAnnotations(
            Class<T> rootNodeType, Set<Long> rootNodeIds, 
            Set<String> annotationTypes, Set<Long> annotatorIds, 
            Parameters options)
    {
    	 Map<Long, Set<A>> map = new HashMap<Long, Set<A>>();
         if (rootNodeIds.size() == 0)  return map;
         if (!IAnnotated.class.isAssignableFrom(rootNodeType)) {
             throw new ApiUsageException(
                     "Class parameter for loadAnnotation() "
                             + "must be a subclass of ome.model.IAnnotated");
         }

         Parameters po = new Parameters();

         Query<List<IAnnotated>> q = getQueryFactory().lookup(
                 PojosFindAnnotationsQueryDefinition.class.getName(),
                 po.addIds(rootNodeIds).addClass(rootNodeType)
                         .addSet("annotatorIds", annotatorIds));

         List<IAnnotated> l = iQuery.execute(q);
         iQuery.clear();
         // no count collection

         // SORT
         Iterator<IAnnotated> i = new HashSet<IAnnotated>(l).iterator();
         IAnnotated annotated;
         Long id; 
         Set<A> set;
         List<A> list;
         List<A> supported;
         Iterator<A> j;
         A object;
         Iterator<A> ann;
         OriginalFile of;
         FileAnnotation fa;
         while (i.hasNext()) {
             annotated = i.next();
             id = annotated.getId();
             set = map.get(id);
             if (set == null) {
                 set = new HashSet<A>();
                 map.put(id, set);
             }
             list = (List<A>) annotated.linkedAnnotationList();
             supported = new ArrayList<A>();
             if (list != null) {
            	 if (annotationTypes != null && annotationTypes.size() > 0) {
            		 j = list.iterator();
            		 
                	 while (j.hasNext()) {
                		 object = j.next();
                		 if (annotationTypes.contains(
                				 object.getClass().getName())) {
                			 supported.add(object);
                		 }
                	 }
            	 } else {
            		 supported.addAll(list);
            	 }
             } else supported.addAll(list);
             ann = supported.iterator();
             while (ann.hasNext()) {
            	 object = ann.next();
            	 //load original file.
            	 if (object instanceof FileAnnotation) {
            		 fa = (FileAnnotation) object;
            		 if (fa.getFile() != null) {
            			 of = iQuery.findByQuery(LOAD_ORIGINAL_FILE, 
                				 new Parameters().addId(fa.getFile().getId()));
				 fa.setFile(of);
            		 }
            	 }
             }
             //Archived if no updated script.
            set.addAll(supported);
         }
         return map;
    }

    /**
     * Implemented as specified by the {@link IMetadata} I/F
     * @see IMetadata#loadSpecifiedAnnotations(Class, Set, Set, Parameters)
     */
    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public <A extends Annotation> Set<A> loadSpecifiedAnnotations(
    		@NotNull Class type, Set<String> include, Set<String> exclude,
    		 Parameters options)
    {
    	List<A> list = getAnnotation(type, include, exclude, options);
    	if (FILE_TYPE.equals(type.getName()) && list != null) {
    		Iterator<A> i = list.iterator();
    		FileAnnotation fa;
    		OriginalFile of;
    		List<Annotation> toRemove = new ArrayList<Annotation>();
    		while (i.hasNext()) {
    			fa = (FileAnnotation) i.next();
    			if (fa.getFile() != null) {
    				of = iQuery.findByQuery(LOAD_ORIGINAL_FILE, 
    	       				 new Parameters().addId(fa.getFile().getId()));
    	       		 fa.setFile(of);
    			} else toRemove.add(fa);
			}
    		if (toRemove.size() > 0) list.removeAll(toRemove);
    	}
    	if (list == null) return new HashSet<A>();
    	
    	return new HashSet<A>(list);
    }
    
    /**
     * Implemented as specified by the {@link IMetadata} I/F
     * @see IMetadata#countSpecifiedAnnotations(Class, Set, Set, Parameters)
     */
    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Long countSpecifiedAnnotations(
    		@NotNull Class type, Set<String> include, Set<String> exclude,
    		 Parameters options)
    {
    	List list = getAnnotation(type, include, exclude, options);
    	if (list != null) return new Long(list.size());
    	return -1L;
    }
    
    /**
     * Implemented as specified by the {@link IMetadata} I/F
     * @see IMetadata#loadAnnotation(Set)
     */
    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public <A extends Annotation> Set<A> loadAnnotation(
    		@NotNull @Validate(Long.class) Set<Long> annotationIds)
    {
    	StringBuilder sb = new StringBuilder();
    	sb.append("select ann from Annotation as ann ");
    	sb.append("left outer join fetch ann.details.creationEvent ");
    	sb.append("left outer join fetch ann.details.owner ");
    	sb.append("where ann.id in (:ids)");
   
    	List<A> list = iQuery.findAllByQuery(sb.toString(), 
    			new Parameters().addIds(annotationIds));
    	if (list == null) return new HashSet<A>();
    	Iterator<A> i = list.iterator();
    	A object;
    	FileAnnotation fa;
    	Object of;
    	while (i.hasNext()) {
			object =  i.next();
			if (object instanceof FileAnnotation) {
				fa = (FileAnnotation) object;
				if (fa.getFile() != null) {
					of = iQuery.findByQuery(LOAD_ORIGINAL_FILE, 
							new Parameters().addId(fa.getFile().getId()));
					fa.setFile((OriginalFile) of);
				}
			}
		}
    	return new HashSet<A>(list);
    }
    
    /**
     * Implemented as specified by the {@link IMetadata} I/F
     * @see IMetadata#loadTagContent(Set, Parameters)
     */
    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Map<Long, Set<IObject>> 
		loadTagContent(@NotNull @Validate(Long.class) Set<Long> tagIds, 
		 Parameters options)
	{
		Map<Long, Set<IObject>> m = new HashMap<Long, Set<IObject>>();
		Iterator<Long> i = tagIds.iterator();
		Long id;
		while (i.hasNext()) {
			id = i.next();
			m.put(id, loadObjects(id, options));
		}
    	return m;
	}
    
    /**
     * Implemented as specified by the {@link IMetadata} I/F
     * @see IMetadata#loadTagSets(Parameters)
     */
    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Set<IObject> loadTagSets(Parameters options)
	{
    	Set result = new HashSet();
    	Parameters po = new Parameters(options);
    	Parameters param = new Parameters();
    	StringBuilder sb = new StringBuilder();
    	sb.append("select link from AnnotationAnnotationLink as link ");
		sb.append("left outer join fetch link.child child ");
		sb.append("left outer join fetch link.parent parent ");
		sb.append("left outer join fetch child.details.owner ownerChild ");
		sb.append("left outer join fetch parent.details.owner ownerParent ");
		sb.append("where child member of "+TAG_TYPE);
		sb.append(" and parent member of "+TAG_TYPE);
		if (po.isExperimenter()) {
			sb.append(" and parent.details.owner.id = :userID");
			param.addLong("userID", po.getExperimenter());
		}
		
    	List l = iQuery.findAllByQuery(sb.toString(), param);
    	List<Long> tagSetIds = new ArrayList<Long>();
    	List<Long> ids = new ArrayList<Long>();
    	List<Long> children = new ArrayList<Long>();
    	Annotation ann;
		Long id;
		Iterator i;
		AnnotationAnnotationLink link;
		//check the tag set-tag link.
    	if (l != null) {
    		i = l.iterator();
    		while (i.hasNext()) {
				link = (AnnotationAnnotationLink) i.next();
				id = link.getId();
				ann = link.parent();
				if (NS_INSIGHT_TAG_SET.equals(ann.getNs())) {
					if (!ids.contains(ann.getId())) {
						ids.add(id);
						result.add(link);
						if (!tagSetIds.contains(ann.getId()))
							tagSetIds.add(ann.getId());
					}
				}
				id = link.getChild().getId();
				if (!children.contains(id))
					children.add(id);
			}
    	}
    	
    	//Retrieve the tagSets not linked to a tag
    	sb = new StringBuilder();
    	Set<String> include = new HashSet<String>();
    	include.add(NS_INSIGHT_TAG_SET);
		
    	sb.append("select ann from Annotation as ann");
		sb.append(" where ann member of "+TAG_TYPE);
		sb.append(" and ann.ns is not null and ann.ns in (:include)");
		
		param = new Parameters();
		param.addSet("include", include);
		if (tagSetIds.size() > 0) {
			sb.append(" and ann.id not in (:ids)");
			param.addIds(tagSetIds);
		}
		if (po.isExperimenter()) {
			sb.append(" and ann.details.owner.id = :userID");
			param.addLong("userID", po.getExperimenter());
		}
		l = iQuery.findAllByQuery(sb.toString(), param);
	    if (l != null) {
	    	i = l.iterator();
    		while (i.hasNext()) {
				result.add((Annotation) i.next());
    		}
	    }
	    
    	//retrieve the orphan tags.
		if (po.isOrphan()) {
			sb = new StringBuilder();
			Set<String> exclude = new HashSet<String>();
			exclude.add(NS_INSIGHT_TAG_SET);
			sb.append("select ann from Annotation as ann");
			sb.append(" where ann member of "+TAG_TYPE);
			sb.append(" and (ann.ns is null or ann.ns not in (:exclude))");
    		
			param = new Parameters();
			param.addSet("exclude", exclude);
			if (children.size() > 0) {
				sb.append(" and ann.id not in (:ids)");
				param.addIds(children);
			}
			if (po.isExperimenter()) {
				sb.append(" and ann.details.owner.id = :userID");
				param.addLong("userID", po.getExperimenter());
			}
			l = iQuery.findAllByQuery(sb.toString(), param);
		    if (l != null) {
		    	i = l.iterator();
	    		while (i.hasNext()) {
					result.add((Annotation) i.next());
	    		}
		    }
		}

		return result;
	}
    
    /**
     * Implemented as specified by the {@link IMetadata} I/F
     * @see IMetadata#lgetTaggedObjectsCount(Set, Parameters)
     */
    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Map getTaggedObjectsCount(@NotNull @Validate(Long.class) 
    		Set<Long> tagIds, Parameters options)
    {
    	Map<Long, Long> counts = new HashMap<Long, Long>();
    	Iterator<Long> i = tagIds.iterator();
    	Long id;
    	while (i.hasNext()) {
			id = i.next();
			counts.put(id, countTaggedObjects(id));
		}
    	return counts;
    }
    
    /**
     * Implemented as specified by the {@link IMetadata} I/F
     * @see IMetadata#loadAnnotationUsedNotOwned(Class, Long)
     */
    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Set<IObject> loadAnnotationsUsedNotOwned(@NotNull Class annotationType,
    		long userID)
    {
    	Set result = new HashSet();
    	String type = annotationType.getName();
    	List<Long> ids = new ArrayList<Long>();
    	Iterator i;
    	IObject o;
    	Parameters param = new Parameters();
    	param.addLong("userID", userID);
    	List<IObject> l;
    	StringBuffer sb = new StringBuffer();
		sb.append("select link from ImageAnnotationLink as link ");
		sb.append("left outer join fetch link.child child ");
		sb.append("left outer join fetch child.details.owner as co ");
		sb.append("left outer join fetch link.details.owner as lo ");
		sb.append("where co.id != :userID and lo.id = :userID " +
				"and child member of "+type);
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null && l.size() > 0) {
			i = l.iterator();
			ImageAnnotationLink link;
			while (i.hasNext()) {
				link = (ImageAnnotationLink) i.next();
				o = link.getChild();
				if (!ids.contains(o.getId())) {
					result.add(o);
					ids.add(o.getId());
				}
			}
		}
		sb = new StringBuffer();
		sb.append("select link from DatasetAnnotationLink as link ");
		sb.append("left outer join fetch link.child child ");
		sb.append("left outer join fetch child.details.owner as co ");
		sb.append("left outer join fetch link.details.owner as lo ");
		sb.append("where co.id != :userID and lo.id = :userID " +
				"and child member of "+type);
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null && l.size() > 0) {
			i = l.iterator();
			DatasetAnnotationLink link;
			while (i.hasNext()) {
				link = (DatasetAnnotationLink) i.next();
				o = link.getChild();
				if (!ids.contains(o.getId())) {
					result.add(o);
					ids.add(o.getId());
				}
			}
		}
		sb = new StringBuffer();
		sb.append("select link from ProjectAnnotationLink as link ");
		sb.append("left outer join fetch link.child child ");
		sb.append("left outer join fetch child.details.owner as co ");
		sb.append("left outer join fetch link.details.owner as lo ");
		sb.append("where co.id != :userID and lo.id = :userID " +
				"and child member of "+type);
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null && l.size() > 0) {
			i = l.iterator();
			ProjectAnnotationLink link;
			while (i.hasNext()) {
				link = (ProjectAnnotationLink) i.next();
				o = link.getChild();
				if (!ids.contains(o.getId())) {
					result.add(o);
					ids.add(o.getId());
				}
			}
		}
		sb = new StringBuffer();
		sb.append("select link from ScreenAnnotationLink as link ");
		sb.append("left outer join fetch link.child child ");
		sb.append("left outer join fetch child.details.owner as co ");
		sb.append("left outer join fetch link.details.owner as lo ");
		sb.append("where co.id != :userID and lo.id = :userID " +
				"and child member of "+annotationType.getName());
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null && l.size() > 0) {
			i = l.iterator();
			ScreenAnnotationLink link;
			while (i.hasNext()) {
				link = (ScreenAnnotationLink) i.next();
				o = link.getChild();
				if (!ids.contains(o.getId())) {
					result.add(o);
					ids.add(o.getId());
				}
			}
		}
		sb = new StringBuffer();
		sb.append("select link from PlateAnnotationLink as link ");
		sb.append("left outer join fetch link.child child ");
		sb.append("left outer join fetch child.details.owner as co ");
		sb.append("left outer join fetch link.details.owner as lo ");
		sb.append("where co.id != :userID and lo.id = :userID " +
				"and child member of "+annotationType.getName());
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null && l.size() > 0) {
			i = l.iterator();
			PlateAnnotationLink link;
			while (i.hasNext()) {
				link = (PlateAnnotationLink) i.next();
				o = link.getChild();
				if (!ids.contains(o.getId())) {
					result.add(o);
					ids.add(o.getId());
				}
			}
		}
		sb = new StringBuffer();
		sb.append("select link from WellSampleAnnotationLink as link ");
		sb.append("left outer join fetch link.child child ");
		sb.append("left outer join fetch child.details.owner as co ");
		sb.append("left outer join fetch link.details.owner as lo ");
		sb.append("where co.id != :userID and lo.id = :userID " +
				"and child member of "+type);
		l = iQuery.findAllByQuery(sb.toString(), param);
		if (l != null && l.size() > 0) {
			i = l.iterator();
			WellSampleAnnotationLink link;
			while (i.hasNext()) {
				link = (WellSampleAnnotationLink) i.next();
				o = link.getChild();
				if (!ids.contains(o.getId())) {
					result.add(o);
					ids.add(o.getId());
				}
			}
		}
    	return result;
    }
    
    /**
     * Implemented as specified by the {@link IMetadata} I/F
     * @see IMetadata#countAnnotationsUsedNotOwned(Class, Long)
     */
    @RolesAllowed("user")
    @Transactional(readOnly = true)
    public Long countAnnotationsUsedNotOwned(@NotNull Class annotationType, 
    		long userID)
    {
    	Set s = loadAnnotationsUsedNotOwned(annotationType, userID);
    	if (s != null) return new Long(s.size());
    	return -1L;
    }

}
