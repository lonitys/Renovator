package com.stilwerk.blueprint.importer;

import com.stilwerk.blueprint.Blueprint;
import java.util.List;

/** An imported blueprint plus human-readable warnings (unknown blocks ...). */
public record ImportResult(Blueprint blueprint, List<String> warnings) {}
