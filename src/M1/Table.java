package M1;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;
import java.text.*;

public class Table implements Serializable
{
    private String strTableName;
    private String strClusteringKeyColumn;
    private Vector<Column> vecColumns;
    private Vector<String> vecPages;
    private Hashtable<String,String> htblColNameType;
    private Hashtable<String,String> htblColNameMin;
    private Hashtable<String,String> htblColNameMax;
    private Hashtable<String,Integer> htblColNameIndex = new Hashtable<>(); //helper
    private int MaximumRowsCountinTablePage, pagesIDcounter = 0;


    public Table(String strTableName, String strClusteringKeyColumn, Hashtable<String,String> htblColNameType,
                 Hashtable<String,String> htblColNameMin,Hashtable<String,String> htblColNameMax , int MaximumRowsCountinTablePage ) {

        this.strTableName = strTableName;
        this.strClusteringKeyColumn = strClusteringKeyColumn;
        this.MaximumRowsCountinTablePage = MaximumRowsCountinTablePage;
        vecColumns = new Vector<>(); // initially ,the vector size is by default 10


        Enumeration<String> strEnumeration = htblColNameType.keys();

        while (strEnumeration.hasMoreElements()) {
            String strColName = strEnumeration.nextElement();
            String strColType = htblColNameType.get(strColName);
            String minVal = htblColNameMin.get(strColName);
            String maxVal = htblColNameMax.get(strColName);


            if (strClusteringKeyColumn.equals(strColName))
                this.vecColumns.add(0,new Column(strColName,strColType,true,minVal,maxVal));
            else
                this.vecColumns.add(new Column(strColName, strColType, false, minVal, maxVal));

        } // end while

        this.vecPages = new Vector<>(0);
        
        
        for (int i = 0; i < vecColumns.size(); i++) {
        	Column column = vecColumns.get(i);
			htblColNameIndex.put(column.getStrColName(), i);
		}

    }
    public  Column getColumn(String colName){
//        for (Column c: getVecColumns()) {
//           if(c.getStrColName().equals(colName))
//               return c;
//        }
//        return null;
    	int index = getColumnEquivalentIndex(colName); //O(1)
    	return (index == -1)? null : getVecColumns().get(index);
    }
    public void validateValueType(Column column ,Object valueToCheck) throws DBAppException,ParseException{
        if(valueToCheck instanceof  String){
                  if(!column.getStrColType().equals("java.lang.String"))
                      throw new DBAppException("The value and its corresponding column must be of the same type");
                  String value =(String) valueToCheck;
                  if(value.compareTo(column.getStrMaxVal()) > 0 ||value.compareTo(column.getStrMinVal()) < 0 )
                      throw new DBAppException("You cannot insert a value that is out of the bounds of this column");
        }
        else if(valueToCheck instanceof  Integer){
            if(!column.getStrColType().equals("java.lang.Integer"))
                throw new DBAppException("The value and its corresponding column must be of the same type");
            Integer value =(Integer) valueToCheck;
            if(value.compareTo(Integer.valueOf(column.getStrMaxVal())) > 0 ||value.compareTo(Integer.valueOf(column.getStrMinVal())) < 0 )
                throw new DBAppException("You cannot insert a value that is out of the bounds of this column");
        }
        else if(valueToCheck instanceof  Double){
            if(!column.getStrColType().equals("java.lang.Double"))
                throw new DBAppException("The value and its corresponding column must be of the same type");
            Double value =(Double) valueToCheck;
            if(value.compareTo(Double.valueOf(column.getStrMaxVal())) > 0 ||value.compareTo(Double.valueOf(column.getStrMinVal())) < 0 )
                throw new DBAppException("You cannot insert a value that is out of the bounds of this column");
        }
        else if (valueToCheck instanceof Date){
            if(!column.getStrColType().equals("java.lang.Date"))
                throw new DBAppException("The value and its corresponding column must be of the same type");
            Date value =(Date) valueToCheck;

            Date dMax = new SimpleDateFormat("yyyy/MM/dd").parse(column.getStrMaxVal());//throw parseException
            Date dMin = new SimpleDateFormat("yyyy/MM/dd").parse(column.getStrMinVal());

            if(value.compareTo(dMax) > 0 ||value.compareTo(dMin) < 0 )
                throw new DBAppException("You cannot insert a value that is out of determined bounds of this column");

        }
        else
            throw new DBAppException("This value type is not supported");

    }
    public void insertAnEntry(Row entry) throws DBAppException{
        if(vecPages.size() == 0){ // table has no page
            Page page = new Page(strTableName,0);
            this.addNewPage(page);
            page.insertAnEntry(entry);
            this.savePageToDisk(page, vecPages.size()-1);
        }
        else{ // there are some pages in the table
            Object clustringKey = entry.getData().get(0) ;
            // need to determine the id of the right page to insert the entry into .
            int pidOfCorrectPage = binarySrch(clustringKey) ;


            Page correctPage = loadPage(pidOfCorrectPage);

            if(!correctPage.isFull()) { // fortunately , there is a place
                correctPage.insertAnEntry(entry);
                this.savePageToDisk(correctPage, vecPages.size()-1);
                return;
            }
            else{  // Unfortunately , page is full
                if(pidOfCorrectPage == vecPages.size()-1  ) { //correct page is last page and it is full
                    Page newPage = new Page(strTableName, pagesIDcounter + 1);
                    this.addNewPage(newPage);

                    if(((Comparable)clustringKey).compareTo(correctPage.getMaxValInThePage()) >= 0)
                         newPage.insertAnEntry(entry);
                    else{
                        Row tmp = correctPage.getData().remove(MaximumRowsCountinTablePage-1);
                        correctPage.insertAnEntry(entry);
                        newPage.insertAnEntry(tmp);
                    }
                    this.savePageToDisk(newPage, vecPages.size()-1);
                    this.savePageToDisk(correctPage, vecPages.size()-2);
                    return;
                }else { // correct page is not last page and it is full
                      int i ;
                      for ( i = pidOfCorrectPage; i <vecPages.size();i++) // check other next pages , if they are all full or not
                          if(loadPage(i).getNoOfCurrentRows() < MaximumRowsCountinTablePage)
                              break;

                      Row tmp;
                      if(i==vecPages.size()) {   // all next pages are full , no option other than creating new page
                          Page newPage = new Page(strTableName, pagesIDcounter+1);
                          this.addNewPage(newPage);
                          for(int j = vecPages.size()-1 ; j> pidOfCorrectPage ; j--) {
                        	  Page fromPage = this.loadPage(j-1), toPage = this.loadPage(j);
                              tmp = fromPage.getData().remove(MaximumRowsCountinTablePage - 1);
                              toPage.insertAnEntry(tmp);
                              this.savePageToDisk(fromPage, j-1);
                              this.savePageToDisk(toPage, j);
                          }
                      }
                      else{                     // some page has an empty place
                          for (int j = i; j > pidOfCorrectPage ; j--) {
                        	  Page fromPage = this.loadPage(j-1), toPage = this.loadPage(j);
                              tmp = fromPage.getData().remove(MaximumRowsCountinTablePage - 1);
                              toPage.insertAnEntry(tmp);
                              this.savePageToDisk(fromPage, j-1);
                              this.savePageToDisk(toPage, j);
                          }
                      }
                      correctPage = this.loadPage(pidOfCorrectPage);
                      correctPage.insertAnEntry(entry);
                      this.savePageToDisk(correctPage, pidOfCorrectPage);
                      return;
                }
            }
        }

    }


//consider these cases in binary searching
/*
----insert 5 in:
_____2,3
-
2
3
-
____6,8
-
6
7
8
___9,10
9
10
-
-
______
*/

/*
-----insert 5 in:
_____1,4
1
2
3
4
____6,9
6
7
8
9
___10,11
10
11
-
-
______
*/	
/* insertion in page sizes 2,3,4 and others in old code */

	//a method to binary search from the pages vector to find the page in which the entry with the given key can be inserted

	public int binarySrch(Object key) throws DBAppException {
		int lo = 0;
		int hi = vecPages.size() - 1;
		int mid = 0;
		while (lo <= hi) {
			mid = (lo + hi) / 2;
			Page p = this.loadPage(mid);
			if(((Comparable) key).compareTo(p.getMaxValInThePage()) <= 0 && ((Comparable) key).compareTo(p.getMinValInThePage()) >= 0)
				return mid;//key within range of page
			else if (((Comparable) key).compareTo(p.getMaxValInThePage()) > 0)
				lo = mid + 1;
			else if (((Comparable) key).compareTo(p.getMinValInThePage()) < 0)
				hi = mid - 1;

//			else  return mid;
		}

        //exited loop without finding a page that contains the key in range
		if(hi == -1){ // reached lower bound (by reaching condition that mid is 0 and key is less than the smallest value in the page so automatically it is less than the min value in the page, then the key is the minimum one)
			return 0;

		} else if(lo == vecPages.size()){ // reached upper bound (same for lower bound but vice versa)
			return vecPages.size() - 1;

		} else if(lo > hi){ 		// NOTE that: we reached a case that lo exceeds hi, so lo is the higher page and hi is the lower page
			if(((Comparable) key).compareTo(loadPage(hi).getMaxValInThePage()) >= 0  // key is greater than the max value of the page at hi
				&& ((Comparable) key).compareTo(loadPage(lo).getMinValInThePage()) <= 0){// key is greater than the min value of the page at lo
					return (!loadPage(hi).isFull()) ? hi : lo;
                        // if the page at hi is not full, return hi, otherwise return lo
				}
                ///////////// example of the case above: insert 5 in:  hi->Page0(1,2,4);  lo->Page1(6,9,10,11)
                ///// first iteration was lo = mid = 0, hi =1
                ///// second iteration was lo = 1 = hi = mid
                ///// 3rd loop iteration was lo = 1, hi = '0'
                ////////so we check that condidtion

		}

		return mid;
	}

    public Page loadPage(int index) throws DBAppException {
        if(index < 0 || index >= vecPages.size())
            throw new DBAppException("Page index not valid or out of bounds");

        String path ="src/resources/tables/"+strTableName+ "/pages/" + vecPages.get(index) + ".ser";
        Page page = (Page) DBApp.deserialize(path);

        return page;
    }

    public void savePageToDisk( Page page , int pageIndex) throws DBAppException {
        String path ="src/resources/tables/"+strTableName+ "/pages/" + vecPages.get(pageIndex) + ".ser";
        DBApp.serialize( path,page );
    }
    public void addNewPage(Page newPage) throws DBAppException { // add new page to the vector of pages
        vecPages.add("page" + pagesIDcounter); // add file name\path to the vector of pages 
        savePageToDisk(newPage, pagesIDcounter++); // save the page to disk
    }

    public String getStrTableName() {
        return strTableName;
    }

    public void setStrTableName(String strTableName) {
        this.strTableName = strTableName;
    }

    public String getStrClusteringKeyColumn() {
        return strClusteringKeyColumn;
    }

    public void setStrClusteringKeyColumn(String strClusteringKeyColumn) {
        this.strClusteringKeyColumn = strClusteringKeyColumn;
    }

    public Vector<Column> getVecColumns() {
        return vecColumns;
    }

    public void setVecColumns(Vector<Column> vecColumns) {
        this.vecColumns = vecColumns;
    }

    public Vector<String> getVecPages() {
        return vecPages;
    }

    public void setVecPages(Vector<String> vecPages) {
        this.vecPages = vecPages;
    }

    public Hashtable<String, String> getHtblColNameType() {
        return htblColNameType;
    }

    public void setHtblColNameType(Hashtable<String, String> htblColNameType) {
        this.htblColNameType = htblColNameType;
    }

    public Hashtable<String, String> getHtblColNameMin() {
        return htblColNameMin;
    }

    public void setHtblColNameMin(Hashtable<String,String> htblColNameMin) {
        this.htblColNameMin = htblColNameMin;
    }

    public Hashtable<String, String> getHtblColNameMax() {
        return htblColNameMax;
    }

    public void setHtblColNameMax(Hashtable<String,String> htblColNameMax) {
        this.htblColNameMax = htblColNameMax;
    }
    
    public int getColumnEquivalentIndex(String colName) {
    	//gets index or returns -1 in case it doesn't exist
		return htblColNameIndex.getOrDefault(colName, -1);
	}

    /*public int getNextNewPageIDToBeCreated() {
        return pagesIDcounter+1;
    }*/

    public String  toString(){
        String strTblOutput = getStrTableName() + " Table \n"  + "-------------------------------" + "\n";
        Iterator iterateOverColumns = getVecColumns().iterator();
        while (iterateOverColumns.hasNext()) {
            String tmp = String.valueOf(iterateOverColumns.next());
            strTblOutput += tmp ;
            if(tmp.equals(strClusteringKeyColumn))
                strTblOutput += "*";
            strTblOutput+= "\t| ";
        }
        strTblOutput+="\n" +"-------------------------------" +"\n";


        for (int i = 0; i < vecPages.size(); i++) {
            Page page;
            try {
                page = this.loadPage(i);
                strTblOutput += "page" + /*(page.getPid())*/ i + "\n" +"-------"+"\n" + page.toString()+"\n";
            } catch (DBAppException e) {
                e.printStackTrace();
            }
        }

        return strTblOutput;

    }
    
    
    public int findRowToUpdORdel(Object key, int candidateIdx) throws DBAppException {
		Vector<Row> candidatePageData = this.loadPage(candidateIdx).getData() ;
		//binary searching on row to be updated or deleted
        int lo = 0, hi = candidatePageData.size() - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (((Comparable) key).compareTo(candidatePageData.get(mid).getData().get(getColumnEquivalentIndex(strClusteringKeyColumn))) < 0)
                hi = mid - 1;
            else if (((Comparable) key).compareTo(candidatePageData.get(mid).getData().get(getColumnEquivalentIndex(strClusteringKeyColumn))) > 0)
                lo = mid + 1;
            else
                return mid;
        }
		
		return -1;
	}
    
    public int deleteRowsWithoutCKey(Hashtable<String, Object> colNameVal) throws DBAppException {
    	int items = 0;
		for (int i = 0; i < vecPages.size(); i++) {
            Page page = loadPage(i);
			Iterator<Row> iterator = page.getData().iterator();
			while (iterator.hasNext()) {
				Row row = (Row) iterator.next();
				boolean ANDING = false;
				
				Enumeration<String> strEnumeration = colNameVal.keys();
				while (strEnumeration.hasMoreElements()) {
					String strColName = strEnumeration.nextElement();
					if(!row.getData().get(getColumnEquivalentIndex(strColName)).equals(colNameVal.get(strColName))) {
						ANDING = false;
						break;
					}else ANDING = true;
				}
				
				if(ANDING /*is true*/) {	
					iterator.remove(); //same as page.deleteEntry(row)
					items++;
				}
			}
			this.savePageToDisk(page, i);
		}
    	
    	
    	return items;
	}
}

