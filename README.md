# verktyg

[![Clojars Project](https://img.shields.io/clojars/v/verktyg.svg)](https://clojars.org/verktyg)

A Clojure/ClojureScript utility library. Essentially bits & pieces that I use over and over again.

Includes:

 * a small library of general-purpose utility functions `verktyg.core`
 * a CSS-in-JS/CLJS solution based on [emotion][emotion] `verktyg.styled`
 * a wrapper around closure library's `HTML5History` `verktyg.history`
 * a small navigation/routing library building on top of `verktyg.history` and [reitit][reitit] `verktyg.nav`
 * miscellaneous [reagent][reagent] utility functions/components `verktyg.reagent`
 * miscellaneous [re-frame][re-frame] interceptors, effects, co-effects, subscriptions, etc `verktyg.reagent`
 * a small & simple (and incomplete) validation library `verktyg.validation`
 * a thin wrapper around the [Apollo GraphQL Client][apollo] `verktyg.graphql`
 * useful JDBC/PostgreSQL extensions `verktyg.jdbc`

[emotion]: https://emotion.sh/
[reitit]: https://metosin.github.io/reitit/
[re-frame]: https://github.com/Day8/re-frame
[reagent]: https://reagent-project.github.io/
[apollo]: https://www.apollographql.com/client
