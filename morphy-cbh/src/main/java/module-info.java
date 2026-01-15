module se.yarin.morphy.cbh {
  requires org.slf4j;
  requires static org.immutables.value;
  requires me.tongfei.progressbar;
  requires java.compiler;
  requires org.jetbrains.annotations;
  requires concurrent.locks;

  exports se.yarin.chess;
  exports se.yarin.chess.annotations;
  exports se.yarin.chess.timeline;
  exports se.yarin.util.parser to
      se.yarin.morphy.cli;
  exports se.yarin.util;
  exports se.yarin.morphy;
  exports se.yarin.morphy.entities;
  exports se.yarin.morphy.entities.filters;
  exports se.yarin.morphy.games;
  exports se.yarin.morphy.games.moves;
  exports se.yarin.morphy.games.filters;
  exports se.yarin.morphy.games.annotations;
  exports se.yarin.morphy.queries;
  exports se.yarin.morphy.qqueries;
  exports se.yarin.morphy.queries.operations;
  exports se.yarin.morphy.queries.joins;
  exports se.yarin.morphy.boosters;
  exports se.yarin.morphy.storage;
  exports se.yarin.morphy.validation;
  exports se.yarin.morphy.exceptions;
  exports se.yarin.morphy.metrics;
  exports se.yarin.morphy.text;
  exports se.yarin.morphy.util;
}
