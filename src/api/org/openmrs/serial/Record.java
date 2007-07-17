package org.openmrs.serial;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import javax.xml.parsers.*;

import org.xml.sax.*;
import org.w3c.dom.*;

/** An XML serialization object using a DOM tree which represents
* itself as a parent-child structure of nodes with attributes to 
* the outside world
*
* A Record in this context is a collection of objects, represented by
* more than one database table as example a 'patient record'
*
* @todo create better exception classes
* @todo obfuscate the notion that this object serializes to XML
* @todo change error handling to proper logging classes
*
* @author juliem
*/
public class Record
{
    // xml structures
	public static final String UTF8 = "UTF-8";
	public static final String NOSTR = "no";
	public static final String YESSTR = "yes";
	public static final String DELIM = ",";
	public static final String NULLSTR = "";

	private Document m_doc = null;
	private Element m_first = null;
    private String m_name=null;

    private Package m_package=null;

    /** Package/record ownership
     */
    protected void setPackage(Package p) {m_package=p;}
    public Package getPackage() {return m_package;}

    /** Provide a name for this Record
     */
    public void setName(String name) throws Exception
    {
        if (m_name!=null)
        {
            throw new Exception("Name already set for this record\n");
        }

        // populate the m_first node
        m_name = name;
        createItem(null, name);
    }

    public String getName() {return m_name;}

    /** Get access to an empty serializer to create a storage package
     */
    protected static Record getEmpty() throws ParserConfigurationException, Exception
    {
        return new Record();
    }

    /** Create a serializer from a previous byte array of UTF8 characters
     * @input byte array
     */
    protected static Record create(byte [] data) throws Exception
    {
        Record xml = new Record();
		xml.init(new ByteArrayInputStream(data));
        return xml;
    }

	/** Construct a document from a stringbuffer of UTF8 characters
     * @input stringbuffer of xml data
	*/
	public static Record create(StringBuffer data) throws Exception
	{
        Record xml = new Record();
		xml.init(new ByteArrayInputStream(data.toString().getBytes(UTF8)));
        return xml;
	}

	/** Construct a document from a string of UTF8 characters
     * @input string of xml data
	*/
	public static Record create(String data) throws Exception
	{
        return create(new StringBuffer(data));
	}

	/** Dump to a stream - there is no thread safety guarantee
     * with regard to this XML tree being manipulated nor with
     * individual nodes being modified
     */
	public synchronized void dump(OutputStream os) throws Exception
	{
		StreamResult res = new StreamResult(os);	
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();
        
        DOMSource source = new DOMSource(m_doc);
        
        transformer.setOutputProperty(OutputKeys.INDENT, YESSTR);
        transformer.setOutputProperty(OutputKeys.ENCODING, UTF8);
        transformer.setOutputProperty(OutputKeys.STANDALONE, YESSTR);
        transformer.transform(source, res);
	}

	/** Create an item and stitch it in
	* @param parent Item
	* @param item name
	*/
	public Item createItem(Item parent, String ename) throws Exception
	{
        Element who = (parent !=null ? parent.getElement() : null);
		Element element = m_doc.createElement(ename);

		if (who==null)
		{
			m_doc.appendChild(element);

			if (m_first==null)
			{
				m_first = element;
			}
		}
		else
		{
			who.appendChild(element);
		}

		return new Item(element);
	}

	/** Set an attribute on an item
	* @param item to modify
	* @param attribute name
	* @param attribute value
	*/
	public void setAttribute(Item e, String sName, String sValue)
		throws Exception
	{
		e.setAttribute(sName, sValue);
	}

	/** Remove an attribute on an item
	* @param item to modify
	* @param attribute name
	* @param attribute value
	*/
	public void removeAttribute(Item e, String sName)
		throws Exception
	{
		e.removeAttribute(sName);
	}

	/** Convert the response for transmit; this will not be
    * url-encoded however
	*/
	public String toString() 
	{
		try {
			return new String(toByteArray(), UTF8);
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/** Convert the response for transmit
	*/
	public byte[] toByteArray() throws Exception
	{
		ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
		dump(os);
		return os.toByteArray();
	}

	/** Return the document; not normally used outside
	* @returns document
	*/
	public final Document getDoc() {return m_doc;}

    public Item getRootItem() throws Exception
    {
        return new Item(m_first);
    }

	/**
	* Get the children of this item
	* @param the item
	* @returns list of immediate children 
	*/
	public final ArrayList <org.openmrs.serial.Item>getItems(Item n)
	{
		return getItems(n, null);
	}

	/**
	* Get the children of this item by item name
	* @param the item
	* @param matching elements (tag name), comma delim seperated ok
	* @returns list of items
	*/
	public final ArrayList <org.openmrs.serial.Item> getItems(Item parent, String itemname)
	{
		ArrayList <org.openmrs.serial.Item>list = new ArrayList<org.openmrs.serial.Item>();
		NodeList nodes = parent.getNode().getChildNodes();
		int sz = nodes.getLength();

		ArrayList <String>tags = new ArrayList<String>();
		int tagsz = 0;

		if (itemname!=null)
		{
			StringTokenizer st = new StringTokenizer(itemname, DELIM);
			while (st.hasMoreTokens())
			{
				tags.add(st.nextToken());
			}
		}

		// Process found elements
		tagsz = tags.size();
		for (int index = 0; index < sz; index++)
		{
			Node node = nodes.item(index);
			if (node.getNodeType() == Node.ELEMENT_NODE)
			{
				String theTag = node.getNodeName();

				// Add all
				if (tagsz == 0)
				{
                    Item item = new Item(nodes.item(index));
					list.add(item);
				}

				// Add matches only
				else
				{
					for (int j = 0; j < tagsz; j++)
					{
						if (theTag.equalsIgnoreCase((String)tags.get(j)))
						{
                            Item item = new Item(nodes.item(index));
                            list.add(item);
							break;	// any node can have at most 1 name
						}
					}
				}
			}
		}

		return list;
	}

    /** Create a text item child
     * @param parent item
     * @param text
     * @return the new item
     */
	public Item createText(Item parent, String data) throws Exception
	{
        Element who = parent.getElement();
		Text element = m_doc.createTextNode(data);

		if (who==null)
		{
			m_doc.appendChild(element);
		}
		else
		{
			who.appendChild(element);
		}

		return new Item(element);
	}

    /** Grab the contents of a text-only item
     */
	public final String getText(Item item)
	{
        return item.getText();
	}

	/** Retrieve cdata like items as a single text string
	* @param Item to grab data
	* @returns collapsed text
	*/
	public final String getData(Item item)
	{
        return item.getData();
	}

	/** Get the number of items under this parent
	* @param parent
	* @returns number of items
	*/
	public final int numItems(Item parent)
	{
        return parent.numItems();
	}

	/** Get the value from an attribute - A bit of overkill
	* @param element
	* @param attribute name
	* @return value
	*/
	public final String getValue(Item item, String a)
	{
        return item.getAttribute(a);
	}

	/** Remove item from parent
	* @param parent
	*/
	public final void removeItem(Item parent, Item item)
	{
		parent.getElement().removeChild(item.getElement());
	}

	/** Find a top level child item with supplied name
	* @param name
	* @return item or null if none found
	*/
	public final Item getItem(String nname)
	{
		return getItemFromNode(m_doc, nname);
	}

	/** Find a named element, child of
	* @param parent
	* @param name
	* @return item or null if none found
	*/
	public final Item getItem(Item parent, String nname)
	{
		return getItemFromNode(parent.getNode(), nname);
    }

    private final Item getItemFromNode(Node node, String nname)
    {
		NodeList nodes = node.getChildNodes();

		int sz = nodes.getLength();
		for (int index = 0; index < sz; index++)
		{
			node = nodes.item(index);
			if (node.getNodeType() == Node.ELEMENT_NODE)
			{
				String name = node.getNodeName();
				if (nname.equalsIgnoreCase(name))
				{
					return new Item((Element)node);
				}
			}
		}

		return null;		// gack
	}

	/** Create an empty serializer object
	*/
	private Record() throws ParserConfigurationException, Exception
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		m_doc = builder.newDocument();
	}

    /** Load an input stream into a (presumed) empty doc
     * @param inputstream of valid XML formatted data
     */
	private void init(InputStream is) throws Exception
    {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder;

			builder = factory.newDocumentBuilder();
			m_doc = builder.parse(is);

			NodeList nodes = m_doc.getChildNodes();
			Node firstChild = nodes.item(0);

            //System.out.println("Parsed first child " + firstChild.getNodeName());

            m_name = firstChild.getNodeName();
            m_first = (Element)firstChild;
		}
		catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}
}