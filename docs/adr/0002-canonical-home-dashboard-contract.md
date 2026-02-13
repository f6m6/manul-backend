# 0002 Canonical Home Dashboard Contract

- Status: Accepted
- Date: 2026-02-13

## Context

The Home screen previously stitched together many endpoints, and `handler.clj` contained embedded SQL for Home metrics. That leaked persistence concerns into the HTTP layer and made `pallas` coordinate backend internals.

## Decision

1. Add a canonical backend endpoint: `GET /home-dashboard`.
2. Keep existing Home endpoints for compatibility, but make Home UI consume `home-dashboard`.
3. Move local Home metrics SQL into a DB view: `public.view_home_x_metrics`.
4. Add a dedicated data-store module (`manul-backend.data.home-x-metrics`) so handlers compose data rather than query raw SQL.

`/home-dashboard` returns:
- `last_gig`
- `home_next_actions`
- `recent_sessions`
- `live_gigs_by_year`
- `sessions_by_year`
- `home_x`

## Consequences

- Cleaner boundary: UI is a thin presenter of one backend-owned contract.
- Cleaner architecture: query logic is pushed from handler to data layer + DB view.
- Safer evolution: adding Home data can happen in one place without multiplying UI fetch coordination.
