package database;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import database.SQLPhrase.KeywordType;
import database.SQLPhrase.PhraseType;

public class SQLLexer {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SQLLexer.class);
	
	private static final Set<String> INSTRUCTION_KEYWORDS = new HashSet<>(Arrays.asList("INSERT", "SELECT DISTINCT", "SELECT", "UPDATE", "DELETE", "SET", "ORDER BY", "CREATE", "ALTER", "MODIFY", "DROP COLUMN", "DROP"));
	
	private static final Set<String> TABLE_POINTER_KEYWORDS = new HashSet<>(Arrays.asList("FROM", "INTO", "UPDATE", "ON", "LEFT JOIN", "RIGHT JOIN", "FULL JOIN", "INNER JOIN", "TABLE"));
	
	private static final Set<String> EXPRESSION_KEYWORDS = new HashSet<>(Arrays.asList( "WHERE", "AND", "VALUES"));
	
	private static final Set<String> JOIN_KEYWORDS = new HashSet<>(Arrays.asList("LEFT JOIN", "RIGHT JOIN", "FULL JOIN", "INNER JOIN"));
	
	private static final Set<String> ORDER_KEYWORDS = new HashSet<>(Arrays.asList("ASC", "DESC"));
	
	private static final Set<String> DATA_TYPE_KEYWORDS = new HashSet<>(getDataTypeKeywords());
	
	private static List<String> allKeywords;
	
	public static List<SQLPhrase> readQuery(String query){
		List<SQLPhrase> output = new ArrayList<>();
		String currentPhrase = "";
		boolean openQuote = false;
		int openBracket = 0;
		boolean dot = false;
		boolean numeric = false;
		Operator operator = null;
		for(int i = 0; i < query.length(); i++) {
			String currentCharacter = query.substring(i, i + 1);
			if (!openQuote) {
				currentCharacter = currentCharacter.toUpperCase();
				if (!numeric && currentPhrase.matches("[0-9\\.]")) {
					numeric = true;
				}
			}
			SQLPhrase newPhrase = null;
			if (currentCharacter.equals(" ") && !openQuote) {
				newPhrase = splitOffSQLPhrase(currentPhrase, i);
				if (numeric) {
					newPhrase.setType(PhraseType.VALUE);
				}
				numeric = false;
			}
			if (currentCharacter.equals("'")) {
				newPhrase = splitOffSQLPhrase(currentPhrase, i);
				if (openQuote) {
					newPhrase.setType(PhraseType.VALUE);
				}
				openQuote = !openQuote;
			}
			if (currentCharacter.equals("(") && !openQuote) {
				newPhrase = splitOffSQLPhrase(currentPhrase, i);
				openBracket++;
			}
			if (currentCharacter.equals(")") && !openQuote) {
				newPhrase = splitOffSQLPhrase(currentPhrase, i);
				if (numeric) {
					SQLPhrase lastPhrase = getLastPhrase(output);
					if (lastPhrase.hasKeywordType(KeywordType.DATA_TYPE)) {
						lastPhrase.setLinkedValue(newPhrase);
						newPhrase.setLinkedDataType(lastPhrase);
						newPhrase.setType(PhraseType.VALUE);
					}
					numeric = false;
				}
				openBracket--;
			}
			if (currentCharacter.equals(",") && !openQuote) {
				newPhrase = splitOffSQLPhrase(currentPhrase, i);
			}
			if (currentCharacter.equals(".") && !openQuote && !numeric) {
				newPhrase = splitOffSQLPhrase(currentPhrase, i);
				newPhrase.setType(PhraseType.TABLE_NAME);
				dot = true;
			}
			if (getOperator(currentPhrase + currentCharacter) != null && !openQuote) {
				operator = getOperator(currentPhrase + currentCharacter);
			}
			if (currentCharacter.equals(";") && !openQuote) {
				newPhrase = splitOffSQLPhrase(currentPhrase, i);
				if (numeric) {
					newPhrase.setType(PhraseType.VALUE);
				}
				numeric = false;
			}
			if (newPhrase != null) {
				if (newPhrase.getString().length() > 0) {
					if (newPhrase.getType() == null) {
						categorizePhrase(newPhrase, output);
					}
					if (operator != null) {
						if (newPhrase.hasType(PhraseType.VALUE)) {
							SQLPhrase previousLinkablePhrase = getLastPhrase(output);
							newPhrase.setLinkedColumn(previousLinkablePhrase);
							newPhrase.setLinkedOperator(operator);
							previousLinkablePhrase.setLinkedValue(newPhrase);
							operator = null;
						}
					}
					if (dot) {
						if (!newPhrase.hasType(PhraseType.TABLE_NAME)) {
							newPhrase.setLinkedTable(getLastPhrase(output));
							getLastPhrase(output).setLinkedColumn(newPhrase);
							dot = false;
						}
					}
					if (openBracket > 0) {
						SQLPhrase previousLinkablePhrase = getLastPhrase(output);
						if (newPhrase.hasKeywordType(KeywordType.DATA_TYPE) && previousLinkablePhrase.hasType(PhraseType.COLUMN_NAME)) {
							previousLinkablePhrase.setLinkedDataType(newPhrase);
							newPhrase.setLinkedColumn(previousLinkablePhrase);
						}
					}
					if (newPhrase.getType() != null) {
						output.add(newPhrase);
					}
				}
				currentPhrase = "";
			}
			else {				
				currentPhrase += currentCharacter;
			}
		}
		return validateStatement(output);
	}
	
	private static void categorizePhrase(SQLPhrase newPhrase, List<SQLPhrase> previousPhrases) {
		SQLPhrase previousPhrase = getLastPhrase(previousPhrases);
		SQLPhrase previousKeyword = getPreviousKeyword(newPhrase, previousPhrases);
		if (getAllKeywords().contains(newPhrase.getString())) {
			newPhrase.setType(PhraseType.KEYWORD);
			newPhrase.setKeywordTypes(getKeywordTypes(newPhrase.getString()));
		}
		else if (previousPhrase == null || previousKeyword == null) {
			return;
		}
		else if (getAllKeywords().contains(previousPhrase.getString() + " " + newPhrase.getString())) {
			String bothPhraseStrings = previousPhrase.getString() + " " + newPhrase.getString();
			previousPhrases.remove(previousPhrase);
			newPhrase.setString(bothPhraseStrings);
			newPhrase.setType(PhraseType.KEYWORD);
			newPhrase.setKeywordTypes(getKeywordTypes(bothPhraseStrings));
		}
		else if(previousKeyword.hasKeywordType(KeywordType.TABLE_POINTER) && previousPhrase.hasType(PhraseType.KEYWORD)) {
			newPhrase.setType(PhraseType.TABLE_NAME);
		}
		else if(previousKeyword.hasKeywordType(KeywordType.INSTRUCTION)) {
			newPhrase.setType(PhraseType.COLUMN_NAME);
			newPhrase.setLinkedInstruction(previousKeyword);
		}
		else if(previousKeyword.hasKeywordType(KeywordType.EXPRESSION)) {
			newPhrase.setType(PhraseType.COLUMN_NAME);
		}
		else if(previousKeyword.hasKeywordType(KeywordType.ORDER)) {
			newPhrase.setType(PhraseType.COLUMN_NAME);
		}
		else if(previousPhrase.hasType(PhraseType.TABLE_NAME)) {
			newPhrase.setType(PhraseType.COLUMN_NAME);
		}
		else if(previousPhrase.hasType(PhraseType.COLUMN_NAME)) {
			newPhrase.setType(PhraseType.COLUMN_NAME);
		}
		else if(matchesTypePattern(previousPhrases, PhraseType.COLUMN_NAME, KeywordType.DATA_TYPE)) {
			newPhrase.setType(PhraseType.COLUMN_NAME);
		}
		else if(matchesTypePattern(previousPhrases, PhraseType.COLUMN_NAME, KeywordType.DATA_TYPE, PhraseType.VALUE)) {
			newPhrase.setType(PhraseType.COLUMN_NAME);
		}
		if (getOperator(newPhrase.getString()) != null) {
			newPhrase.setType(null);
		}
	}
	
	private static List<SQLPhrase> validateStatement(List<SQLPhrase> statement){
		boolean valid = true;
		for(SQLPhrase phrase : statement) {
			if (phrase.getType() == null) {
				valid = false;				
				LOGGER.warn("Invalid SQLPhrase : {}", phrase.getString());
			}
		}
		if(valid) {
			return statement;
		}
		return Collections.emptyList();
	}
	
	private static SQLPhrase splitOffSQLPhrase(String currentPhrase, int index) {
		SQLPhrase newPhrase = new SQLPhrase(currentPhrase);
		index ++;
		return newPhrase;
	}
	
	private static Operator getOperator(String input) {
		for(Operator operator : Operator.values()) {
			if (operator.getString().equals(input)) {
				return operator;
			}
		}
		return null;
	}
	
	public static boolean matchesTypePattern(List<SQLPhrase> sqlPhrases, Object... types) {
		if (sqlPhrases.size() < types.length) {
			return false;
		}
		List<SQLPhrase> phrasesToTest = sqlPhrases.subList(sqlPhrases.size() - types.length, sqlPhrases.size());
		for(int i = 0; i < types.length; i++) {
			SQLPhrase phrase = phrasesToTest.get(i);
			if (!phrase.hasType(types[i])) {
				return false;
			}
		}
		return true;
	}
	
	public static SQLPhrase getLastPhrase(List<SQLPhrase> phrases) {
		return phrases.size() > 0 ? phrases.get(phrases.size() - 1) : null;
	}
	
	public static SQLPhrase getPreviousPhrase(SQLPhrase currentPhrase, List<SQLPhrase> allPhrases) {
		return allPhrases.get(allPhrases.indexOf(currentPhrase) - 1);
	}
	
	public static SQLPhrase getPreviousKeyword(SQLPhrase currentPhrase, List<SQLPhrase> allPhrases) {
		if (allPhrases.size() == 0) {
			return null;
		}
		int upTo = allPhrases.size() - 1;
		if (allPhrases.contains(currentPhrase)) {
			upTo = allPhrases.indexOf(currentPhrase) - 1;
		}
		for(int i = upTo; i >= 0 ; i--) {
			SQLPhrase earlierPhrase = allPhrases.get(i);
			if (earlierPhrase.getType() == PhraseType.KEYWORD) {
				return earlierPhrase;
			}
		}
		return null;
	}
	
	private static final List<String> getDataTypeKeywords(){
		return Arrays.asList(DataType.values()).stream().map(dt -> dt.name()).collect(Collectors.toList());
	}
	
	private static final List<String> getAllKeywords(){
		if (allKeywords == null) {
			allKeywords = new ArrayList<>();
			allKeywords.addAll(INSTRUCTION_KEYWORDS);
			allKeywords.addAll(TABLE_POINTER_KEYWORDS);
			allKeywords.addAll(EXPRESSION_KEYWORDS);
			allKeywords.addAll(JOIN_KEYWORDS);
			allKeywords.addAll(ORDER_KEYWORDS);
			allKeywords.addAll(DATA_TYPE_KEYWORDS);
		}
		return allKeywords;
	}
	
	private static List<KeywordType> getKeywordTypes(String phrase) {
		Map<KeywordType, Set<String>> keywordGroupMap = new HashMap<>();
		keywordGroupMap.put(KeywordType.INSTRUCTION, INSTRUCTION_KEYWORDS);
		keywordGroupMap.put(KeywordType.TABLE_POINTER, TABLE_POINTER_KEYWORDS);
		keywordGroupMap.put(KeywordType.EXPRESSION, EXPRESSION_KEYWORDS);
		keywordGroupMap.put(KeywordType.JOIN, JOIN_KEYWORDS);
		keywordGroupMap.put(KeywordType.ORDER, ORDER_KEYWORDS);
		keywordGroupMap.put(KeywordType.DATA_TYPE, DATA_TYPE_KEYWORDS);
		List<KeywordType> keywordTypes = new ArrayList<>();
		for(KeywordType keywordType : keywordGroupMap.keySet()) {
			if (keywordGroupMap.get(keywordType).contains(phrase)) {
				keywordTypes.add(keywordType);
			}
		}		
		return keywordTypes;
	}
}
