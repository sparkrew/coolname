package io.github.chains_project.coolname.api_finder.model;

import java.util.List;

/**
 * Data class to hold class members (constructors, setters, getters).
 */
public record ClassMemberData(List<String> constructors, List<String> setters, List<String> getters) {
}
