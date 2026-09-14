# Olympus Web UI

Graph-based web UI for [hive-mcp](https://github.com/hive-agi/hive-mcp) swarm visualization.

Built with ClojureScript, re-frame, and inspired by [re-frame-flow](https://github.com/ertugrulcetin/re-frame-flow).

## Vision

Olympus Web UI provides real-time visualization of:

- **Agent Topology** - Ling hierarchy and coordination
- **Knowledge Graph** - Memory entries, edges, and staleness
- **Hivemind Events** - Real-time event stream visualization

## Architecture

```
┌─────────────────────────────────────────┐
│     Olympus Web UI (this repo)          │
│  ┌─────────────────────────────────┐    │
│  │  re-frame-flow graph renderer   │    │
│  │  - Agent graph (lings)          │    │
│  │  - KG visualization             │    │
│  └─────────────────────────────────┘    │
│              ↕ WebSocket                │
├─────────────────────────────────────────┤
│  hive-mcp (Clojure backend)             │
│  - Hivemind events                      │
│  - Agent registry                       │
└─────────────────────────────────────────┘
```

## Tech Stack

- **ClojureScript** - Language
- **shadow-cljs** - Build tooling
- **re-frame** - State management
- **re-frame-flow** - Graph visualization foundation
- **WebSocket** - Real-time connection to hive-mcp

## Development

```bash
# Install dependencies
npm install

# Start shadow-cljs dev server
npx shadow-cljs watch app

# Open http://localhost:8080
```

## Project Structure

```
src/
├── olympus/
│   ├── core.cljs        # Entry point
│   ├── db.cljs          # App state
│   ├── events.cljs      # re-frame events
│   ├── subs.cljs        # re-frame subscriptions
│   ├── views.cljs       # UI components
│   ├── websocket.cljs   # hive-mcp connection
│   └── graphs/
│       ├── agent.cljs   # Agent topology
│       └── kg.cljs      # Knowledge graph
```

## Roadmap

- [ ] Basic re-frame scaffold
- [ ] WebSocket connection to hive-mcp
- [ ] Agent status dashboard
- [ ] KG explorer
- [ ] Hivemind event stream

## Related Projects

- [hive-mcp](https://github.com/hive-agi/hive-mcp) - Multi-agent coordination engine
- [re-frame-flow](https://github.com/ertugrulcetin/re-frame-flow) - Event chain visualization

## License

AGPL-3.0-or-later

Copyright (C) 2026 Pedro Gomes Branquinho (BuddhiLW)
