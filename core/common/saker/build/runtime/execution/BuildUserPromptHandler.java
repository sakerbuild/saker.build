package saker.build.runtime.execution;

import java.util.List;

public interface BuildUserPromptHandler {
	public int prompt(String title, String message, List<String> options);
}