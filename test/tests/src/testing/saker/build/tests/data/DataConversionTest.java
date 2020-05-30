/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package testing.saker.build.tests.data;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ReflectTypes;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.util.data.ConversionContext;
import saker.build.util.data.ConversionFailedException;
import saker.build.util.data.DataConverter;
import saker.build.util.data.DataConverterUtils;
import saker.build.util.data.GenericArgumentLocation;
import saker.build.util.data.annotation.ConverterConfiguration;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.TestUtils;

@SakerTest
@SuppressWarnings("unused")
public class DataConversionTest extends SakerTestCase {
	private static ParameterizedType pt(Class<?> type, Type... templateargs) {
		return ReflectTypes.makeParameterizedType(type, templateargs);
	}

	private static WildcardType wct(Type... templateargs) {
		return ReflectTypes.makeWildcardTypeExtends(templateargs);
	}

	private static class SuperClass {
		private int field1 = 1;
		private int field2 = 2;

		public SuperClass() {
		}

		public SuperClass(int field1, int field2) {
			this.field1 = field1;
			this.field2 = field2;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + field1;
			result = prime * result + field2;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SuperClass other = (SuperClass) obj;
			if (field1 != other.field1)
				return false;
			if (field2 != other.field2)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "SuperClass [field1=" + field1 + ", field2=" + field2 + "]";
		}

	}

	private static class SubClass extends SuperClass {
		private int field2 = 3;
		private int field3 = 4;

		public SubClass() {
		}

		public SubClass(int field1, int field2, int field22, int field3) {
			super(field1, field2);
			this.field2 = field22;
			this.field3 = field3;
		}

		public SubClass(int value1, int value2) {
			this.field2 = value1;
			this.field3 = value2;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + field2;
			result = prime * result + field3;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (getClass() != obj.getClass())
				return false;
			SubClass other = (SubClass) obj;
			if (field2 != other.field2)
				return false;
			if (field3 != other.field3)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "SubClass [field2=" + field2 + ", field3=" + field3 + ", "
					+ (super.toString() != null ? "toString()=" + super.toString() : "") + "]";
		}
	}

	/**
	 * @see ConverterConfiguration
	 */
	@ConverterConfiguration(TestDataConverter.class)
	@ConverterConfiguration(value = TestDataConverter.class, genericArgumentIndex = { 0 })
	@ConverterConfiguration(value = TestDataConverter.class, genericArgumentIndex = { 0, 0 })
	@ConverterConfiguration(value = TestDataConverter.class, genericArgumentIndex = { 0, 1 })
	public static List<Map<String, Integer>> convertfield;

	private static class TestDataConverter implements DataConverter {
		public static final Map<Type, Object> CONVERSION_REQUESTS = new HashMap<>();
		public static final Set<GenericArgumentLocation> CONVERSION_GENERIC_LOCATIONS = new HashSet<>();

		@Override
		public Object convert(ConversionContext conversioncontext, Object value, Type targettype)
				throws ConversionFailedException {
			System.out.println("DataConversionTest.TestDataConverter.convert() " + value);
			CONVERSION_REQUESTS.put(targettype, value);
			CONVERSION_GENERIC_LOCATIONS.add(conversioncontext.getCurrentGenericLocation());
			return DataConverterUtils.convertDefault(conversioncontext, value, targettype);
		}

	}

	@Override
	public void runTest(Map<String, String> parameters) throws Exception {
//		testConverterValueOf();

		convertToTypeVariable();
		convertToWildcard();
		convertToGenericArray();
		convertToParameterized();
		convertToIterable();
		convertToArray();
		convertToString();
		convertToEnum();
		convertWithValueOf();
		convertValueToMethod();
		convertWithToAndValueOf();

		testAnnotationConversions();

		testRecursiveConversion();

		Object current;

		ParameterizedType listint = pt(List.class, int.class);
		ParameterizedType listinteger = pt(List.class, Integer.class);
		ParameterizedType listlistint = pt(List.class, listint);

		Type listintarray = ReflectTypes.makeArrayType(listint);
		Type listlistintarray = ReflectTypes.makeArrayType(listlistint);
		Type intarray = ReflectUtils.getArrayClassWithComponent(int.class);
		Type intintarray = ReflectUtils.getArrayClassWithComponent(int[].class);

		ParameterizedType mapobjectobject = pt(Map.class, Object.class, Object.class);

		assertConvertibleExamineEquals(1, int.class, 1);
		assertConvertibleExamineEquals(2, Integer.class, 2);
		assertConvertibleExamineEquals(3.3d, int.class, 3);
		assertConvertibleExamineEquals(4.4d, Integer.class, 4);
		assertConvertibleExamineEquals(1, int[].class, new int[] { 1 });
		assertConvertibleExamineEquals(1, List.class, listOf(1));

		current = "1";
		assertConvertibleExamineEquals(current, int.class, 1);
		assertConvertibleExamineEquals(current, Integer.class, 1);
		assertConvertibleExamineEquals(current, intarray, new int[] { 1 });
		assertConvertibleExamineEquals(current, List.class, listOf(current));
		assertConvertibleExamineEquals(current, listint, listOf(1));
		assertConvertibleExamineEquals(current, listinteger, listOf(1));
		assertConvertibleExamineEquals(current, listlistint, listOf(listOf(1)));
		assertConvertibleExamineEquals(current, listintarray, new List<?>[] { listOf(1) });
		assertConvertibleExamineEquals(current, listlistintarray, new List<?>[] { listOf(listOf(1)) });
		assertConvertibleExamineEquals(current, intintarray, new int[][] { new int[] { 1 } });

		current = listOf("1");
		assertConvertibleExamineEquals(current, intarray, new int[] { 1 });
		assertConvertibleExamineEquals(current, listint, listOf(1));
		assertConvertibleExamineEquals(current, listinteger, listOf(1));
		assertConvertibleExamineEquals(current, listlistint, listOf(listOf(1)));

		current = listOf(listOf("1"));
		assertConvertibleExamineEquals(current, listlistint, listOf(listOf(1)));
		assertConvertibleExamineEquals(current, intintarray, new int[][] { new int[] { 1 } });

		current = new Object[] {};
		assertConvertibleExamine(current, listint);
		assertConvertibleExamine(current, listlistint);

		current = new int[] { 1 };
		assertConvertibleExamineEquals(current, listint, listOf(1));
		assertConvertibleExamineEquals(current, listlistint, listOf(listOf(1)));

		current = new int[] { 1, 2 };
		assertConvertibleExamineEquals(current, List.class, listOf(1, 2));
		assertConvertibleExamineEquals(current, listint, listOf(1, 2));
		assertConvertibleExamineEquals(current, listlistint, listOf(listOf(1), listOf(2)));

		assertConvertibleExamine(1, Number.class);
		assertConvertibleExamine(listOf(1, 2), pt(List.class, wct()));
		assertConvertibleExamine(listOf(1, 2), pt(List.class, wct(Number.class)));

//		assertConvertibleExamineEquals(new SubClass(), mapobjectobject,
//				ObjectUtils.treeMapBuilder().put("field1", 1).put("field2", 3).put("field3", 4).build());
//		assertConvertibleExamineEquals(new SuperClass(), mapobjectobject, ObjectUtils.treeMapBuilder().put("field1", 1).put("field2", 2).build());

//		assertConvertibleExamineEquals(ObjectUtils.treeMapBuilder().put("field1", 123).put("field3", 321).put("nofield", "novalue").build(), SubClass.class,
//				new SubClass(123, 2, 3, 321));
//		assertConvertibleExamineEquals(ObjectUtils.treeMapBuilder().put("value1", 123).put("value2", 321).put("nofield", "novalue").build(), SubClass.class,
//				new SubClass(1, 2, 123, 321));

		{
			TreeMap<String, Object> complexmap = new TreeMap<>();
			TreeMap<Object, Object> fieldmap = new TreeMap<>();
			complexmap.put("Field", fieldmap);
			fieldmap.put("first", "1");
			fieldmap.put("second", "2");
			ComplexField complex = DataConverterUtils.wrapMappedInterface(complexmap, ComplexField.class);
			assertEquals(complex.getField(), fieldmap);
			assertEquals(complex.getField("first"), "1");
			assertEquals(complex.getField("second"), "2");
			assertEquals(complex.getField("nonexistent"), null);
		}
		{
			Field convertf = ReflectUtils.getDeclaredFieldAssert(DataConversionTest.class, "convertfield");
			Map<String, Integer> mapelem = ImmutableUtils.singletonMap("hello", 123);
			List<Map<String, Integer>> converttestval = ImmutableUtils.asUnmodifiableArrayList(mapelem);
			@SuppressWarnings("unchecked")
			List<Map<String, Integer>> val = (List<Map<String, Integer>>) DataConverterUtils.convert(null,
					converttestval, convertf);
			Type fieldtype = convertf.getGenericType();
			Type maptype = ((ParameterizedType) fieldtype).getActualTypeArguments()[0];

			assertMap(TestDataConverter.CONVERSION_REQUESTS).contains(fieldtype, converttestval).noRemaining();
			assertEquals(TestDataConverter.CONVERSION_GENERIC_LOCATIONS, setOf(GenericArgumentLocation.INSTANCE_ROOT));
			TestDataConverter.CONVERSION_GENERIC_LOCATIONS.clear();
			TestDataConverter.CONVERSION_REQUESTS.clear();

			Map<String, Integer> convertedmap = val.iterator().next();
			assertMap(TestDataConverter.CONVERSION_REQUESTS).contains(maptype, mapelem).noRemaining();
			assertEquals(TestDataConverter.CONVERSION_GENERIC_LOCATIONS,
					setOf(GenericArgumentLocation.INSTANCE_ROOT.child(0)));
			TestDataConverter.CONVERSION_GENERIC_LOCATIONS.clear();
			TestDataConverter.CONVERSION_REQUESTS.clear();

			Entry<String, Integer> entry = convertedmap.entrySet().iterator().next();
			entry.getKey();
			assertMap(TestDataConverter.CONVERSION_REQUESTS).contains(String.class, "hello").noRemaining();
			assertEquals(TestDataConverter.CONVERSION_GENERIC_LOCATIONS,
					setOf(GenericArgumentLocation.INSTANCE_ROOT.child(0).child(0)));
			TestDataConverter.CONVERSION_GENERIC_LOCATIONS.clear();
			TestDataConverter.CONVERSION_REQUESTS.clear();

			entry.getValue();
			assertMap(TestDataConverter.CONVERSION_REQUESTS).contains(Integer.class, 123).noRemaining();
			assertEquals(TestDataConverter.CONVERSION_GENERIC_LOCATIONS,
					setOf(GenericArgumentLocation.INSTANCE_ROOT.child(0).child(1)));
		}

		testEnumAdaptation();
	}

	private static class StringDataClass {
		private String s;

		protected StringDataClass(String s) {
			this.s = s;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((s == null) ? 0 : s.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			StringDataClass other = (StringDataClass) obj;
			if (s == null) {
				if (other.s != null)
					return false;
			} else if (!s.equals(other.s))
				return false;
			return true;
		}
	}

	private static class DirectValueOf extends StringDataClass {
		public DirectValueOf(String s) {
			super(s);
		}

		public static DirectValueOf valueOf(String s) {
			return new DirectValueOf(s);
		}
	}

	private static class ObjectValueOf extends StringDataClass {
		public ObjectValueOf(String s) {
			super(s);
		}

		public static ObjectValueOf valueOf(Object o) {
			return new ObjectValueOf(Objects.toString(o, null));
		}
	}

	private static class IntegerValueOf extends StringDataClass {
		public IntegerValueOf(String s) {
			super(s);
		}

		public static IntegerValueOf valueOf(Integer i) {
			return new IntegerValueOf(Integer.toString(i));
		}
	}

	private static class IntValueOf extends StringDataClass {
		public IntValueOf(String s) {
			super(s);
		}

		public static IntValueOf valueOf(int i) {
			return new IntValueOf(Integer.toString(i));
		}
	}

	private static class DualIntValueOf extends StringDataClass {
		public DualIntValueOf(String s) {
			super(s);
		}

		public static DualIntValueOf valueOf(Integer i) {
			return new DualIntValueOf(Integer.toString(i));
		}

		public static DualIntValueOf valueOf(int i) {
			//the Integer variant should be called
			throw fail();
		}
	}

	private static class NumberValueOf extends StringDataClass {
		public NumberValueOf(String s) {
			super(s);
		}

		public static NumberValueOf valueOf(Number i) {
			return new NumberValueOf(i.toString());
		}
	}

	private static class MultiAvailableNumberValueOf extends StringDataClass {
		private Class<?> valueofc;

		public MultiAvailableNumberValueOf(String s, Class<?> valueofc) {
			super(s);
			this.valueofc = valueofc;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + ((valueofc == null) ? 0 : valueofc.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (getClass() != obj.getClass())
				return false;
			MultiAvailableNumberValueOf other = (MultiAvailableNumberValueOf) obj;
			if (valueofc == null) {
				if (other.valueofc != null)
					return false;
			} else if (!valueofc.equals(other.valueofc))
				return false;
			return true;
		}

		//TODO document that valueof arguments must be raw or have only ? wildcards without bounds
		public static MultiAvailableNumberValueOf valueOf(Comparable<?> i) {
			if (i.equals(1)) {
				return new MultiAvailableNumberValueOf(i.toString(), Comparable.class);
			}
			throw fail();
		}

		public static MultiAvailableNumberValueOf valueOf(Number i) {
			if (i.equals(2)) {
				return new MultiAvailableNumberValueOf(i.toString(), Number.class);
			}
			throw fail();
		}

		public static MultiAvailableNumberValueOf valueOf(Object i) {
			if (i.equals(3)) {
				return new MultiAvailableNumberValueOf(i.toString(), Object.class);
			}
			throw fail();
		}

		public static MultiAvailableNumberValueOf valueOf(Serializable i) {
			if (i.equals(4)) {
				return new MultiAvailableNumberValueOf(i.toString(), Serializable.class);
			}
			throw fail();
		}

		public static MultiAvailableNumberValueOf valueOf(String i) {
			if (i.equals("5")) {
				return new MultiAvailableNumberValueOf(i.toString(), String.class);
			}
			throw fail();
		}
	}

	private static class SomeInterfaceValueOf extends StringDataClass {
		public SomeInterfaceValueOf(String s) {
			super(s);
		}

		public static SomeInterfaceValueOf valueOf(SomeInterface i) {
			return new SomeInterfaceValueOf(i.getValue());
		}
	}

	private static class ToConvertValue {
		private String i;

		public ToConvertValue(String i) {
			this.i = i;
		}

		public ToConvertTarget toToConvertTarget() {
			return new ToConvertTarget(i);
		}
	}

	private static class ToConvertValueCanonical {
		private String i;

		public ToConvertValueCanonical(String i) {
			this.i = i;
		}

		public ToConvertTarget totesting_saker_build_tests_data_DataConversionTest_ToConvertTarget() {
			return new ToConvertTarget(i);
		}
	}

	private static class ToSubConvertValue {
		private String i;

		public ToSubConvertValue(String i) {
			this.i = i;
		}

		public SubToConvertTarget toToConvertTarget() {
			return new SubToConvertTarget(i);
		}
	}

	private static class ToConvertTarget {
		private String i;

		public ToConvertTarget(String i) {
			this.i = i;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((i == null) ? 0 : i.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ToConvertTarget other = (ToConvertTarget) obj;
			if (i == null) {
				if (other.i != null)
					return false;
			} else if (!i.equals(other.i))
				return false;
			return true;
		}
	}

	private static class SubToConvertTarget extends ToConvertTarget {
		public SubToConvertTarget(String i) {
			super(i);
		}
	}

	public static class AdaptToConvertValue {
		private String val;

		public AdaptToConvertValue(String val) {
			this.val = val;
		}

		public SomeInterface toSomeInterface() {
			return new SomeInterfaceImpl(val);
		}

		public SomeInterfaceImpl toSomeInterfaceImpl() {
			return new SomeInterfaceImpl(val);
		}
	}

	private void convertWithToAndValueOf() throws AssertionError, Exception {
		assertConvertibleExamineEquals(new AdaptToConvertValue("1"), SomeInterfaceValueOf.class,
				new SomeInterfaceValueOf("1"));
	}

	private void convertValueToMethod() throws AssertionError, Exception {
		assertConvertibleExamineEquals(new ToConvertValue("1"), ToConvertTarget.class, new ToConvertTarget("1"));
		assertConvertibleExamineEquals(new ToSubConvertValue("1"), ToConvertTarget.class, new SubToConvertTarget("1"));
		assertConvertibleExamineEquals(new ToConvertValueCanonical("1"), ToConvertTarget.class,
				new ToConvertTarget("1"));

		//test adapting the result of the to...() method
		ClassLoader cl = TestUtils.createClassLoaderForClasses(AdaptToConvertValue.class, SomeInterface.class,
				SomeInterfaceImpl.class);
		Object cladapttoconvertvalueinstance = Class.forName(AdaptToConvertValue.class.getName(), false, cl)
				.getConstructor(String.class).newInstance("1");
		SomeInterface adaptconverted = DataConverterUtils.convert(cladapttoconvertvalueinstance, SomeInterface.class);
		assertEquals(adaptconverted.getValue(), "1");
		//as it has been adapted, they shouldn't equal
		assertNotEquals(adaptconverted, new SomeInterfaceImpl("1"));

		//it should fail, as the result of toSomeInterfaceImpl() is not adaptable, and not assignable
		assertConvertFail(cladapttoconvertvalueinstance, SomeInterfaceImpl.class);
		//it should work without different classloaders
		assertConvertibleExamineEquals(new AdaptToConvertValue("1"), SomeInterfaceImpl.class,
				new SomeInterfaceImpl("1"));
	}

	private void convertWithValueOf() throws AssertionError, Exception {
		assertConvertibleExamineEquals("str", DirectValueOf.class, new DirectValueOf("str"));
		assertConvertibleExamineEquals("str", ObjectValueOf.class, new ObjectValueOf("str"));
		assertConvertibleExamineEquals(1, IntegerValueOf.class, new IntegerValueOf("1"));
		assertConvertibleExamineEquals(1, IntValueOf.class, new IntValueOf("1"));
		assertConvertibleExamineEquals(1, DualIntValueOf.class, new DualIntValueOf("1"));
		assertConvertibleExamineEquals(1, NumberValueOf.class, new NumberValueOf("1"));
		assertConvertibleExamineEquals(1, MultiAvailableNumberValueOf.class,
				new MultiAvailableNumberValueOf("1", Comparable.class));
		assertConvertibleExamineEquals(2, MultiAvailableNumberValueOf.class,
				new MultiAvailableNumberValueOf("2", Number.class));
		assertConvertibleExamineEquals(3, MultiAvailableNumberValueOf.class,
				new MultiAvailableNumberValueOf("3", Object.class));
		assertConvertibleExamineEquals(4, MultiAvailableNumberValueOf.class,
				new MultiAvailableNumberValueOf("4", Serializable.class));
		assertConvertibleExamineEquals(5, MultiAvailableNumberValueOf.class,
				new MultiAvailableNumberValueOf("5", String.class));

		assertConvertibleExamineEquals(new SomeInterfaceImpl("s"), SomeInterfaceValueOf.class,
				new SomeInterfaceValueOf("s"));

		//test adaptation of the valueof argument
		ClassLoader cl = TestUtils.createClassLoaderForClasses(SomeInterface.class, SomeInterfaceImpl.class);
		Object clsomeinterfaceimplinstance = Class.forName(SomeInterfaceImpl.class.getName(), false, cl)
				.getConstructor(String.class).newInstance("s");
		assertConvertibleExamineEquals(clsomeinterfaceimplinstance, SomeInterfaceValueOf.class,
				new SomeInterfaceValueOf("s"));
	}

	private void convertToEnum() throws AssertionError, Exception {
		assertConvertible("NON_ANONYM", AnonymEnumConvert.NON_ANONYM.getClass());
		assertConvertible("ANONYM", AnonymEnumConvert.ANONYM.getClass());

		assertConvertible(AnonymEnumConvert.NON_ANONYM, AnonymEnumConvert.NON_ANONYM.getClass());
		assertConvertible(AnonymEnumConvert.NON_ANONYM, AnonymEnumConvert.ANONYM.getClass());
		assertConvertible(AnonymEnumConvert.ANONYM, AnonymEnumConvert.NON_ANONYM.getClass());
		assertConvertible(AnonymEnumConvert.ANONYM, AnonymEnumConvert.ANONYM.getClass());

		ClassLoader cl = TestUtils.createClassLoaderForClasses(AnonymEnumConvert.NON_ANONYM.getClass(),
				AnonymEnumConvert.ANONYM.getClass());
		Class<?> enumclclass = Class.forName(AnonymEnumConvert.class.getName(), false, cl);

		assertConvertible("NON_ANONYM", enumclclass);
		assertConvertible("ANONYM", enumclclass);
	}

	private void convertToString() throws Exception {
		assertConvertibleExamineEquals(1, String.class, "1");
		assertConvertibleExamineEquals(null, String.class, null);
	}

	private void convertToArray() throws Exception {
		assertConvertibleExamineEquals(new Object[] { "1" }, Object[].class, new Object[] { "1" });
		assertConvertibleExamineEquals(new Object[] { "1" }, int[].class, new int[] { 1 });
		assertConvertibleExamineEquals(new Object[] { "1" }, Integer[].class, new Integer[] { 1 });

		assertConvertibleExamineEquals(new String[] { "1" }, Object[].class, new Object[] { "1" });
		assertConvertibleExamineEquals(new String[] { "1" }, int[].class, new int[] { 1 });
		assertConvertibleExamineEquals(new String[] { "1" }, Integer[].class, new Integer[] { 1 });

		assertConvertibleExamineEquals(listOf("1"), Object[].class, new Object[] { "1" });
		assertConvertibleExamineEquals(listOf("1"), int[].class, new int[] { 1 });
		assertConvertibleExamineEquals(listOf("1"), Integer[].class, new Integer[] { 1 });

		assertConvertibleExamineEquals("1", Object[].class, new Object[] { "1" });
		assertConvertibleExamineEquals("1", int[].class, new int[] { 1 });
		assertConvertibleExamineEquals("1", Integer[].class, new Integer[] { 1 });

		assertConvertibleExamineEquals(null, int[].class, null);
		assertConvertibleExamineEquals(null, Integer[].class, null);
	}

	private void convertToIterable() throws Exception {
		//elements are not converted, as we're not converting to parameterized iterables
		assertConvertibleExamineEquals(new Object[] { 1 }, List.class, listOf(1));
		assertConvertibleExamineEquals(listOf(1), List.class, listOf(1));
		assertConvertibleExamineEquals(1, List.class, listOf(1));

		assertConvertibleExamineEquals(new Object[] { 1 }, Collection.class, listOf(1));
		assertConvertibleExamineEquals(listOf(1), Collection.class, listOf(1));
		assertConvertibleExamineEquals(1, Collection.class, listOf(1));

		assertConvertibleExamineEquals(new Object[] { 1 }, Iterable.class, listOf(1));
		assertConvertibleExamineEquals(listOf(1), Iterable.class, listOf(1));
		assertConvertibleExamineEquals(1, Iterable.class, listOf(1));
	}

	private void convertToParameterized() throws Exception {
		ParameterizedType intlisttype = ReflectTypes.makeParameterizedType(List.class, Integer.class);
		ParameterizedType intcollectiontype = ReflectTypes.makeParameterizedType(Collection.class, Integer.class);
		ParameterizedType intiterabletype = ReflectTypes.makeParameterizedType(Iterable.class, Integer.class);
		ParameterizedType mapintinttype = ReflectTypes.makeParameterizedType(Map.class, Integer.class, Integer.class);
		ParameterizedType enumretentionpolicytype = ReflectTypes.makeParameterizedType(Enum.class,
				RetentionPolicy.class);

		assertConvertibleExamineEquals("1", intlisttype, listOf(1));
		assertConvertibleExamineEquals("1", intcollectiontype, listOf(1));
		assertConvertibleExamineEquals("1", intiterabletype, listOf(1));
		assertConvertibleExamineEquals(Collections.singletonMap("1", "2"), mapintinttype,
				Collections.singletonMap(1, 2));

		assertConvertibleExamineEquals(RetentionPolicy.SOURCE.name(), enumretentionpolicytype, RetentionPolicy.SOURCE);

		//converting Class<>s
		assertConvertibleExamineEquals(Runnable.class, ReflectTypes.makeParameterizedType(Class.class), Runnable.class);
		assertConvertFail(Thread.class, ReflectTypes.makeParameterizedType(Class.class, Runnable.class));
		assertConvertibleExamineEquals(Runnable.class, ReflectTypes.makeParameterizedType(Class.class, Runnable.class),
				Runnable.class);
		assertConvertibleExamineEquals(Thread.class,
				ReflectTypes.makeParameterizedType(Class.class, ReflectTypes.makeWildcardTypeExtends(Runnable.class)),
				Thread.class);
		assertConvertibleExamineEquals(Thread.class, ReflectTypes.makeParameterizedType(Class.class,
				ReflectTypes.makeWildcardTypeExtends(Thread.class, Runnable.class)), Thread.class);

		assertConvertibleExamineEquals(Runnable.class, ReflectTypes.makeParameterizedType(Class.class,
				ReflectTypes.makeWildcardTypeSuper(Thread.class, Runnable.class)), Runnable.class);

		//convert Class<List> to  Class<List<Runnable>>
		assertConvertibleExamineEquals(List.class, ReflectTypes.makeParameterizedType(Class.class,
				ReflectTypes.makeParameterizedType(List.class, Runnable.class)), List.class);
		//convert Class<List[]> to Class<List<Runnable>[]>
		assertConvertibleExamineEquals(List[].class,
				ReflectTypes.makeParameterizedType(Class.class,
						ReflectTypes.makeArrayType(ReflectTypes.makeParameterizedType(List.class, Runnable.class))),
				List[].class);

		//class conversions between classloaders
		// only if the classes have same names
		ClassLoader cl = TestUtils.createClassLoaderForClasses(SomeRunnableSubInterface.class);
		Class<?> clsomerunnablesubinterface = Class.forName(SomeRunnableSubInterface.class.getName());

		assertConvertibleExamineEquals(clsomerunnablesubinterface,
				ReflectTypes.makeParameterizedType(Class.class, SomeRunnableSubInterface.class),
				SomeRunnableSubInterface.class);
		assertConvertibleExamineEquals(clsomerunnablesubinterface, ReflectTypes.makeParameterizedType(Class.class),
				clsomerunnablesubinterface);
		assertConvertibleExamineEquals(SomeRunnableSubInterface.class,
				ReflectTypes.makeParameterizedType(Class.class, clsomerunnablesubinterface),
				clsomerunnablesubinterface);

		assertConvertibleExamineEquals(clsomerunnablesubinterface,
				ReflectTypes.makeParameterizedType(Class.class, ReflectTypes.makeWildcardTypeExtends(Runnable.class)),
				clsomerunnablesubinterface);

		//converting Constructor<>s
		Constructor<Thread> constructor = ReflectUtils.getConstructorAssert(Thread.class, Runnable.class);
		assertConvertibleExamineEquals(constructor, ReflectTypes.makeParameterizedType(Constructor.class), constructor);
		assertConvertibleExamineEquals(constructor, ReflectTypes.makeParameterizedType(Constructor.class, Thread.class),
				constructor);
		assertConvertFail(constructor, ReflectTypes.makeParameterizedType(Constructor.class, Runnable.class));
		assertConvertibleExamineEquals(constructor, ReflectTypes.makeParameterizedType(Constructor.class,
				ReflectTypes.makeWildcardTypeExtends(Runnable.class)), constructor);

		assertConvertibleExamineEquals(constructor, ReflectTypes.makeParameterizedType(Constructor.class,
				ReflectTypes.makeWildcardTypeExtends(Thread.class, Runnable.class)), constructor);
		assertConvertibleExamineEquals(constructor,
				ReflectTypes.makeParameterizedType(Constructor.class, ReflectTypes.makeWildcardTypeSuper(Thread.class)),
				constructor);
	}

	private void convertToGenericArray() throws AssertionError {
		Type intlistarraytype = ReflectTypes
				.makeArrayType(ReflectTypes.makeParameterizedType(List.class, Integer.class));
		assertConvertibleExamineEquals(listOf(listOf("1")), intlistarraytype, new List<?>[] { listOf(1) });
		assertConvertibleExamineEquals(listOf("1"), intlistarraytype, new List<?>[] { listOf(1) });
		assertConvertibleExamineEquals("1", intlistarraytype, new List<?>[] { listOf(1) });
	}

	private void convertToWildcard() throws AssertionError {
		assertConvertEquals("1", ReflectTypes.makeWildcardTypeExtends(int.class), 1);
		assertConvertEquals("1", ReflectTypes.makeWildcardTypeExtends(Integer.class), 1);
		//no conversion
		assertConvertEquals("1", ReflectTypes.makeWildcardTypeExtends(), "1");
		assertConvertFail("1", ReflectTypes.makeWildcardTypeExtends(Number.class, Integer.class));

		assertConvertEquals("1", ReflectTypes.makeWildcardTypeSuper(), "1");
		assertConvertEquals("1", ReflectTypes.makeWildcardTypeSuper(Integer.class), "1");
		assertConvertEquals("1", ReflectTypes.makeWildcardTypeSuper(Integer.class, Thread.class, Supplier.class), "1");
	}

	private void convertToTypeVariable() throws AssertionError {
		//cannot convert to type variable
		assertException(ConversionFailedException.class,
				() -> DataConverterUtils.convert(1, List.class.getTypeParameters()[0]));
	}

	public static class Recursive1 {
//		@Converter
		public Recursive2 toRecursive2() {
			return new Recursive2();
		}
	}

	public static class Recursive2 {
//		@Converter
		public Recursive1 toRecursive1() {
			return new Recursive1();
		}
	}

	public static class Recursive3 {
		public static Recursive3 valueOf(Recursive4 rec4) {
			return new Recursive3();
		}
	}

	public static class Recursive4 {
		public static Recursive4 valueOf(Recursive3 rec3) {
			return new Recursive4();
		}
	}

	public static class Circle1 {
		public static Circle1 valueOf(Circle2 val) {
			return new Circle1();
		}
	}

	public static class Circle2 {
		public static Circle2 valueOf(Circle3 val) {
			return new Circle2();
		}
	}

	public static class Circle3 {
		public static Circle3 valueOf(Circle1 val) {
			return new Circle3();
		}
	}

	public static class ToBack {
		public static ToBack valueOf(ToBackC o) {
			return new ToBack();
		}
	}

	public static class ToBackC {
		public static ToBackC valueOf(ToBack1 o) {
			throw new IllegalArgumentException();
		}

		public static ToBackC valueOf(ToBack2 o) {
			throw new IllegalArgumentException();
		}
	}

	public static class ToBack1 {
		public static ToBack1 valueOf(Object o) {
			return new ToBack1();
		}
	}

	public static class ToBack2 {
		public static ToBack2 valueOf(Object o) {
			return new ToBack2();
		}
	}

	private void testRecursiveConversion() throws AssertionError {
		assertException(ConversionFailedException.class, () -> {
			DataConverterUtils.convert(new Object(), Recursive4.class);
		});
		assertException(ConversionFailedException.class, () -> {
			DataConverterUtils.convert(new Object(), Circle1.class);
		});
		assertException(ConversionFailedException.class, () -> {
			DataConverterUtils.convert(new Object(), ToBack.class);
		});
	}

	private static final Object TAG_SOMEINTERFACE = new Object();

	public interface SomeRunnableSubInterface extends Runnable {
	}

	public interface SomeInterface {
		public String getValue();

		public void setValue(String Value);
	}

	public static class SomeInterfaceImpl implements SomeInterface {
		private String value;

		public SomeInterfaceImpl(String value) {
			this.value = value;
		}

		@Override
		public String getValue() {
			return value;
		}

		@Override
		public void setValue(String value) {
			this.value = value;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((value == null) ? 0 : value.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SomeInterfaceImpl other = (SomeInterfaceImpl) obj;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}
	}

	public interface ComplexField {
		public Map<String, ?> getField();

		public String getField(String key);
	}

//	interface TargetConvert {
//		public static TargetConvert create() {
//			return ReflectUtil.createMappedObject(TargetConvert.class);
//		}
//
//		public String getTargetValue();
//
//		public void setTargetValue(String TargetValue);
//
//		public static TargetConvert valueOf(String str) {
//			System.out.println("DataConversionTest.TargetConvert.valueOf() String " + str);
//			TargetConvert result = create();
//			result.setTargetValue(str);
//			return result;
//		}
//
//		public static TargetConvert valueOf(SomeInterface sitf) {
//			System.out.println("DataConversionTest.TargetConvert.valueOf() SomeInterface " + sitf);
//			DataConverterUtils.expectObjectTag(sitf, "tag_sitf", TAG_SOMEINTERFACE);
//			TargetConvert result = create();
//			result.setTargetValue(sitf.getValue());
//			return result;
//		}
//	}
//
//	private void testConverterValueOf() throws AssertionError {
//		System.out.println("DataConversionTest.testConverterValueOf() TAG " + TAG_SOMEINTERFACE);
//		SomeInterface si1 = SomeInterface.create("valid");
//		SomeInterface si2 = SomeInterface.create("invalid");
//
//		DataConverterUtils.setObjectTag(si1, "tag_sitf", TAG_SOMEINTERFACE);
//		DataConverterUtils.setObjectTag(si2, "tag_sitf", new Object());
//
//		TargetConvert c1 = (TargetConvert) assertConvertible(si1, TargetConvert.class);
//		TargetConvert c2 = DataConverterUtils.convert(si2, TargetConvert.class);
//
//		System.out.println("DataConversionTest.testConverterValueOf() " + c1);
//		System.out.println("DataConversionTest.testConverterValueOf() " + c2);
//
//		assertEquals(c1.getTargetValue(), "valid");
//		assertEquals(c2.getTargetValue(), si2.toString());
//	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface Annot {
		public int intval() default 1;

		public Class<?> clazz() default SuperClass.class;

		public float[] floats() default {};

		public String[] strings() default {};

		public AnnotEnum enumval() default AnnotEnum.FIRST;

		public Retention sub() default @Retention(RetentionPolicy.CLASS);
	}

	public static enum AnnotEnum {
		FIRST,
		SECOND,
		THIRD;
	}

	@Annot
	public int a1;
	@Annot(sub = @Retention(RetentionPolicy.SOURCE))
	public int a2;
	@Annot(floats = { 1.1f, 2.2f })
	public int a3;
	@Annot(enumval = AnnotEnum.FIRST)
	public int a4;
	@Annot(clazz = SuperClass.class)
	public int a5;
	@Annot(strings = { "123", "asd" })
	public int a6;
	@Annot(intval = 3)
	public int a7;

	public enum AdaptedEnum {
		//make them inner anonymous for more checks
		FIRST {
		},
		SECOND {
		};
	}

	public interface EnumAdaptInterface {
		public AdaptedEnum getEnum();
	}

	public static class EnumAdaptedImpl implements EnumAdaptInterface {
		@Override
		public AdaptedEnum getEnum() {
			return AdaptedEnum.FIRST;
		}

	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void testEnumAdaptation() throws InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
		ClassLoader cl = TestUtils.createClassLoaderForClasses(AdaptedEnum.FIRST.getClass(),
				AdaptedEnum.SECOND.getClass(), AdaptedEnum.class, EnumAdaptedImpl.class, EnumAdaptInterface.class);
		EnumAdaptInterface adapted = (EnumAdaptInterface) DataConverterUtils.adaptInterface(getClass().getClassLoader(),
				ReflectUtils.newInstance(cl.loadClass(EnumAdaptedImpl.class.getName())));
		assertIdentityEquals(adapted.getEnum(), AdaptedEnum.FIRST);

		Enum clenum = Enum.valueOf((Class) cl.loadClass(AdaptedEnum.class.getName()), "SECOND");
		Enum adaptedenum = (Enum) DataConverterUtils.adaptInterface(getClass().getClassLoader(), clenum);
		assertIdentityEquals(adaptedenum, AdaptedEnum.SECOND);
	}

	private void testAnnotationConversions() throws Exception {
		assertAnnotationsEqual(TestUtils.treeMapBuilder().build(), "a1");
		assertAnnotationsEqual(TestUtils.treeMapBuilder()
				.put("sub", TestUtils.treeMapBuilder().put("value", "SOURCE").build()).build(), "a2");
		assertAnnotationsEqual(TestUtils.treeMapBuilder().put("floats", listOf(1.1f, 2.2f)).build(), "a3");
		assertAnnotationsEqual(TestUtils.treeMapBuilder().put("enumval", "FIRST").build(), "a4");
		assertAnnotationsEqual(TestUtils.treeMapBuilder().put("clazz", SuperClass.class).build(), "a5");
		assertAnnotationsEqual(TestUtils.treeMapBuilder().put("strings", listOf(123, "asd")).build(), "a6");
		assertAnnotationsEqual(TestUtils.treeMapBuilder().put("intval", 3).build(), "a7");

		//test converting to a system class
		assertAnnotationsEqual(TestUtils.treeMapBuilder().put("value", "RUNTIME").build(),
				Annot.class.getAnnotation(Retention.class));
	}

	private void assertAnnotationsEqual(Map<?, ?> map, Annotation fieldannot) throws Exception {
		Class<? extends Annotation> annottype = fieldannot.annotationType();
		Annotation converted = (Annotation) assertConvertibleExamineEquals(map, annottype, fieldannot);
		assertEquals(fieldannot.hashCode(), converted.hashCode());
		assertEquals(fieldannot.annotationType(), converted.annotationType());
	}

	private void assertAnnotationsEqual(Map<?, ?> map, String fieldname) throws AssertionError, Exception {
		Annot fieldannot = DataConversionTest.class.getDeclaredField(fieldname).getAnnotation(Annot.class);
		assertAnnotationsEqual(map, fieldannot);
	}

	public static void assertConvertEquals(Object value, Type targettype, Object expected) throws AssertionError {
		try {
			assertEquals(DataConverterUtils.convert(value, targettype), expected);
		} catch (ConversionFailedException e) {
			throw new AssertionError("Value: " + deepToString(value) + " is not convertible to: " + targettype, e);
		}
	}

	public static void assertConvertFail(Object value, Type targettype) {
		assertException(ConversionFailedException.class, () -> DataConverterUtils.convert(value, targettype));
	}

	public static Object assertConvertible(Object value, Type targettype) throws AssertionError {
		try {
			return DataConverterUtils.convert(value, targettype);
		} catch (ConversionFailedException e) {
			throw new AssertionError("Value: " + deepToString(value) + " is not convertible to: " + targettype, e);
		}
	}

	private static void examineConverted(Object object) {
		if (object == null) {
			return;
		}
		if (object instanceof Iterable) {
			Iterator<?> it = ((Iterable<?>) object).iterator();
			while (it.hasNext()) {
				examineConverted(it.next());
			}
		} else if (object instanceof Supplier) {
			examineConverted(((Supplier<?>) object).get());
		} else if (object.getClass().isArray()) {
			int len = Array.getLength(object);
			for (int i = 0; i < len; i++) {
				examineConverted(Array.get(object, i));
			}
		} else if (object instanceof Enumeration) {
			Enumeration<?> en = (Enumeration<?>) object;
			while (en.hasMoreElements()) {
				examineConverted(en.nextElement());
			}
		} else if (object instanceof Map) {
			Map<?, ?> map = (Map<?, ?>) object;
			for (Entry<?, ?> entry : map.entrySet()) {
				examineConverted(entry.getKey());
				examineConverted(entry.getValue());
			}
		}
	}

	public static void assertConvertibleExamine(Object value, Type type) throws AssertionError {
		try {
			Object converted = DataConverterUtils.convert(value, type);
			examineConverted(converted);
		} catch (ConversionFailedException e) {
			throw new AssertionError("Value: " + value + " is not convertible to: " + type, e);
		}
	}

	public static Object assertConvertibleExamineEquals(Object value, Type type, Object equalexpected)
			throws AssertionError {
		try {
			Object converted = DataConverterUtils.convert(value, type);
			examineConverted(converted);
			assertEquals(converted, equalexpected);
			return converted;
		} catch (ConversionFailedException e) {
			throw new AssertionError("Value: " + value + " is not convertible to: " + type, e);
		}
	}
}

//this is package level so there are no noclassdeffounderrors during testing
enum AnonymEnumConvert {
	NON_ANONYM,
	ANONYM {
	};
}