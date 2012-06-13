package org.tdl.vireo.search.impl;

import groovy.json.StringEscapeUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.apache.commons.codec.net.URLCodec;
import org.tdl.vireo.model.Person;
import org.tdl.vireo.model.PersonRepository;
import org.tdl.vireo.model.jpa.JpaPersonImpl;
import org.tdl.vireo.search.ActiveSearchFilter;
import org.tdl.vireo.search.SearchFilter;

import play.Logger;
import play.modules.spring.Spring;

/**
 * An URI based implementation of active search.
 * 
 * The encode and decode strings generated by this are escaped using URI
 * escaping rules, thus it's inherintly URI safe.
 * 
 * @author <a href="http://www.scottphillips.com">Scott Phillips</a>
 */
public class UriActiveSearchFilterImpl implements ActiveSearchFilter {

	// Spring injection
	public PersonRepository personRepo = null;

	// Danamic variables
	public List<String> searchText = new ArrayList<String>();
	public List<String> states = new ArrayList<String>();
	public List<Person> assignees = new ArrayList<Person>();
	public List<Integer> graduationYears = new ArrayList<Integer>();
	public List<Integer> graduationMonths = new ArrayList<Integer>();
	public List<String> degrees = new ArrayList<String>();
	public List<String> departments = new ArrayList<String>();
	public List<String> colleges = new ArrayList<String>();
	public List<String> majors = new ArrayList<String>();
	public List<String> documentTypes = new ArrayList<String>();
	public Boolean umiRelease = null;
	public Date rangeStart = null;
	public Date rangeEnd = null;
	
	/**
	 * Use Spring to construct this object.
	 */
	private UriActiveSearchFilterImpl() {
		// Use Spring
	}
	
	/**
	 * @param personRepo
	 *            The person repository to use for looking up people.
	 */
	public void setPersonRepository(PersonRepository personRepo) {
		this.personRepo = personRepo;
	}
	
	@Override
	public List<String> getSearchText() {
		return searchText;
	}

	@Override
	public void addSearchText(String text) {
		searchText.add(text);
	}

	@Override
	public void removeSearchText(String text) {
		searchText.remove(text);
	}

	@Override
	public List<String> getStates() {
		return states;
	}

	@Override
	public void addState(String state) {
		states.add(state);
	}

	@Override
	public void removeState(String state) {
		states.remove(state);
	}

	@Override
	public List<Person> getAssignees() {
		return assignees;
	}

	@Override
	public void addAssignee(Person assignee) {
		assignees.add(assignee);
	}

	@Override
	public void removeAssignee(Person assignee) {
		assignees.remove(assignee);
	}

	@Override
	public List<Integer> getGraduationYears() {
		return graduationYears;
	}

	@Override
	public void addGraduationYear(Integer year) {
		graduationYears.add(year);
	}

	@Override
	public void removeGraduationYear(Integer year) {
		graduationYears.remove((Object)year);
	}

	@Override
	public List<Integer> getGraduationMonths() {
		return graduationMonths;
	}

	@Override
	public void addGraduationMonth(Integer month) {
		graduationMonths.add(month);
	}

	@Override
	public void removeGraduationMonth(Integer month) {
		graduationMonths.remove((Object)month);
	}

	@Override
	public List<String> getDegrees() {
		return degrees;
	}

	@Override
	public void addDegree(String degree) {
		degrees.add(degree);
	}

	@Override
	public void removeDegree(String degree) {
		degrees.remove(degree);
	}

	@Override
	public List<String> getDepartments() {
		return departments;
	}

	@Override
	public void addDepartment(String department) {
		departments.add(department);
	}

	@Override
	public void removeDepartment(String department) {
		departments.remove(department);
	}

	@Override
	public List<String> getColleges() {
		return colleges;
	}

	@Override
	public void addCollege(String college) {
		colleges.add(college);
	}

	@Override
	public void removeCollege(String college) {
		colleges.remove(college);
	}

	@Override
	public List<String> getMajors() {
		return majors;
	}

	@Override
	public void addMajor(String major) {
		majors.add(major);
	}

	@Override
	public void removeMajor(String major) {
		majors.remove(major);
	}

	@Override
	public List<String> getDocumentTypes() {
		return documentTypes;
	}

	@Override
	public void addDocumentType(String documentType) {
		documentTypes.add(documentType);
	}

	@Override
	public void removeDocumentType(String documentType) {
		documentTypes.remove(documentTypes);
	}

	@Override
	public Boolean getUMIRelease() {
		return umiRelease;
	}

	@Override
	public void setUMIRelease(Boolean value) {
		umiRelease = value;
	}

	@Override
	public Date getDateRangeStart() {
		return rangeStart;
	}

	@Override
	public Date getDateRangeEnd() {
		return rangeEnd;
	}

	@Override
	public void setDateRange(Date start, Date end) {
		rangeStart = start;
		rangeEnd = end;
	}
	
	/**
	 * The string generated by this encoding will be of the form:
	 * 
	 * :value1,value2:value1,value2:
	 * 
	 * Each list of values such as searchText, degrees, departments, etc, are
	 * separated by ":" and inside each of those lists individual values are
	 * separated by ",". Thus the null search filter would be: "::::::::::::::"
	 * All the lists would be null in this case.
	 */
	@Override
	public String encode() {
		
		// Format: :one,two:other,bob:
		
		StringBuilder result = new StringBuilder();
		result.append(":");
		
		// Handle all the lists.
		encodeList(result,searchText);
		encodeList(result,states);
		encodeList(result,assignees);
		encodeList(result,graduationYears);
		encodeList(result,graduationMonths);
		encodeList(result,degrees);
		encodeList(result,departments);
		encodeList(result,colleges);
		encodeList(result,majors);
		encodeList(result,documentTypes);
		
		// Handle the single values.
		if (umiRelease != null) {
			result.append(umiRelease.toString());
		}
		result.append(":");
		
		if (rangeStart != null) {
			result.append(rangeStart.getTime());
		}
		result.append(":");
		
		if (rangeEnd != null) {
			result.append(rangeEnd.getTime());
		}
		result.append(":");
		
		return result.toString();
	}
	
	@Override
	public void decode(String encoded) {
		try {
			String[] split = encoded.split(":",-1);
			if (split.length != 15)
				throw new IllegalArgumentException("Unable to decode active search filter because it does not have the 15 expected number of components instead it has "+split.length);

			// Decode all the lists
			searchText = decodeList(split[1],String.class);
			states = decodeList(split[2],String.class);
			assignees = decodeList(split[3],Person.class);
			graduationYears = decodeList(split[4],Integer.class);
			graduationMonths = decodeList(split[5],Integer.class);
			degrees = decodeList(split[6],String.class);
			departments = decodeList(split[7],String.class);
			colleges = decodeList(split[8],String.class);
			majors = decodeList(split[9],String.class);
			documentTypes = decodeList(split[10],String.class);

			// Handle the single values
			if ("true".equalsIgnoreCase(split[11])) {
				System.out.print("1");
				umiRelease = true;
			} else if ("false".equalsIgnoreCase(split[11])) {
				System.out.print("2");
				umiRelease = false;
			} else {
				System.out.print("3");
				umiRelease = null;
			}

			if (split[12].length() != 0) {
				try {
					rangeStart = new Date(Long.valueOf(split[12]));
				} catch (RuntimeException re) {
					Logger.warn("Unable to decode value '"+split[12]+"' for rangeStart.");
				}
			} else {
				rangeStart = null;
			}

			if (split[13].length() != 0) {
				try {
					rangeEnd = new Date(Long.valueOf(split[13]));
				} catch (RuntimeException re) {
					Logger.warn("Unable to decode value '"+split[13]+"' for rangeEnd.");
				}
			} else {
				rangeEnd = null;
			}
		} catch (RuntimeException re) {
			// If anything other than the specific cases we have allready caught
			// just wrapped the exception with more information.
			throw new RuntimeException("Unable to decode the search filter: " + encoded, re);
		}
	}
	
	@Override
	public void copyTo(SearchFilter other) {
		
		// We're going to be sneaky and take advantage of the fact that the list return by all filters are mutable.
		
		other.getSearchText().clear();
		other.getSearchText().addAll(this.searchText);
		
		other.getStates().clear();
		other.getStates().addAll(this.states);
		
		other.getAssignees().clear();
		other.getAssignees().addAll(this.assignees);
		
		other.getGraduationYears().clear();
		other.getGraduationYears().addAll(this.graduationYears);
		
		other.getGraduationMonths().clear();
		other.getGraduationMonths().addAll(this.graduationMonths);
		
		other.getDegrees().clear();
		other.getDegrees().addAll(this.degrees);
		
		other.getDepartments().clear();
		other.getDepartments().addAll(this.departments);
		
		other.getColleges().clear();
		other.getColleges().addAll(this.colleges);
		
		other.getMajors().clear();
		other.getMajors().addAll(this.majors);
		
		other.getDocumentTypes().clear();
		other.getDocumentTypes().addAll(this.documentTypes);
		
		other.setUMIRelease(this.umiRelease);
		other.setDateRange(this.rangeStart, this.rangeEnd);
	}

	@Override
	public void copyFrom(SearchFilter other) {
		
		this.searchText = new ArrayList<String>(other.getSearchText());
		this.states = new ArrayList<String>(other.getStates());
		this.assignees = new ArrayList<Person>(other.getAssignees());
		this.graduationYears = new ArrayList<Integer>(other.getGraduationYears());
		this.graduationMonths = new ArrayList<Integer>(other.getGraduationMonths());
		this.degrees = new ArrayList<String>(other.getDegrees());
		this.departments = new ArrayList<String>(other.getDepartments());
		this.colleges = new ArrayList<String>(other.getColleges());
		this.majors = new ArrayList<String>(other.getMajors());
		this.documentTypes = new ArrayList<String>(other.getDocumentTypes());
		this.umiRelease = other.getUMIRelease();
		this.rangeStart = other.getDateRangeStart();
		this.rangeEnd = other.getDateRangeEnd();
	}
	
	
	
	/**
	 * Internal method to decode an individual list of items from its serialized
	 * form.
	 * 
	 * @param encoded
	 *            The encoded string.
	 * @param type
	 *            The type of object expected.
	 * @return A list of encoded objects
	 */
	protected <T> List<T> decodeList(String encoded, Class<T> type) {
		
		
		String[] split = encoded.split(",");
		
		List<T> result = new ArrayList<T>();
		for (String raw : split) {
			if (raw.length() ==0)
				continue;
			
			try {
				if ( type == String.class) {
					// List type is string
					result.add( (T) unescape(raw));
				
				} else if (type == Integer.class) {
					// List type is integer, grad month or year
					Integer value = Integer.valueOf(raw);
					result.add((T) value);
					
				} else if (type == Person.class){
					// List type is person, just for assignee
					Long personId = Long.valueOf(raw);
					Person person = personRepo.findPerson(personId);
					result.add((T) person);
				}
			} catch (RuntimeException re) {
				// Just log the error but keep on trucking. One legitimate
				// reason why this may fail is if a person has been deleted
				// since this filter was created, thus the person's id would no
				// longer be valid.
				Logger.warn("Unable to decode value '"+raw+"' for type "+type.getName());
			}
		}
		
		return result;
	}
	
	/**
	 * Encode the list of provided objects. The result stringbuilder will be
	 * modified to include a comma separated list of values, and for convenience
	 * will end with a trailing ":". If the value type is string then it will be
	 * URI encoded prior to putting in the list.
	 * 
	 * Only three datatypes are supported by this method: String, Integer, and
	 * Person. everything else will result in an error.
	 * 
	 * @param result
	 *            Where the encoded list will be appended.
	 * @param values
	 *            The values to encode.
	 */
	protected void encodeList(StringBuilder result, List<?> values) {

		boolean first = true;
		for (Object value: values) {
			if (first)
				first = false;
			else
				result.append(",");
			
			if (value instanceof String) {
				// Plain old strings, the most common case.
				result.append(escape((String)value));
			
			} else if (value instanceof Integer){
				// Integers from grad month & year
				result.append(String.valueOf((Integer) value));
				
			} else if (value instanceof Person) {
				// Full person object from assignee
				Long personId = ((Person) value).getId();
				result.append(String.valueOf(personId));
			} else {
				throw new IllegalArgumentException("Enable to encode unexpected object type: "+value.getClass().getName());
			}
		}
		
		// Signify end of list
		result.append(":");
	}
	
	/**
	 * URI escape the provided value. This method will alse ensure that all ":"
	 * and "," are escaped as well even though they not nessesaraly be.
	 * Otherwise they will interfear with the encoding of other paramaters.
	 * 
	 * @param raw
	 *            The raw value to be escaped.
	 * @return The resulting escaped value.
	 */
	protected String escape(String raw) {
		String escapped = URLEncoder.encode(raw);
		escapped = escapped.replaceAll(",", "%2C");
		escapped = escapped.replaceAll(":", "%3A");
		return escapped;
	}

	/**
	 * Unescape a previously escaped value. This will just do a regular URI
	 * escapeing on the value.
	 * 
	 * @param escapped
	 *            The escaped value.
	 * @return The original raw string.
	 */
	protected String unescape(String escapped) {
		return URLDecoder.decode(escapped);
	}
	

}