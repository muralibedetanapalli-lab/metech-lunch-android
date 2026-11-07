import { useEffect, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import type { TrackedLocation } from "../api/types";
import { Icon } from "./icons";

const riderIcon = L.divIcon({
  className: "rider-marker",
  html: '<span class="rider-pulse"></span><span class="rider-dot">🛵</span>',
  iconSize: [44, 44],
  iconAnchor: [22, 22],
});

/**
 * OpenStreetMap with the partner's latest position, accuracy circle and trail since pickup.
 * Follows the rider unless the viewer has panned away; "Recenter" snaps back.
 */
export function LiveMap({ location, trail }: { location: TrackedLocation; trail: TrackedLocation[] }) {
  const el = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const marker = useRef<L.Marker | null>(null);
  const circle = useRef<L.Circle | null>(null);
  const line = useRef<L.Polyline | null>(null);
  const start = useRef<L.CircleMarker | null>(null);
  const [following, setFollowing] = useState(true);
  const followingRef = useRef(true);
  followingRef.current = following;

  // Create the map once.
  useEffect(() => {
    if (!el.current || map.current) return;
    const m = L.map(el.current, { zoomControl: true, attributionControl: true }).setView([location.latitude, location.longitude], 15);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(m);
    m.on("dragstart", () => setFollowing(false));
    map.current = m;
    return () => {
      m.remove();
      map.current = null;
      marker.current = circle.current = line.current = start.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Update layers whenever a new fix arrives.
  useEffect(() => {
    const m = map.current;
    if (!m) return;
    const here = L.latLng(location.latitude, location.longitude);
    const path = trail.map((p) => L.latLng(p.latitude, p.longitude));

    if (!line.current) line.current = L.polyline(path, { color: "#1f5f3f", weight: 5, opacity: 0.75, lineJoin: "round" }).addTo(m);
    else line.current.setLatLngs(path);

    if (path.length > 1) {
      if (!start.current) {
        start.current = L.circleMarker(path[0], { radius: 6, color: "#fff", weight: 2, fillColor: "#1f5f3f", fillOpacity: 1 })
          .bindTooltip("Picked up here")
          .addTo(m);
      } else start.current.setLatLng(path[0]);
    }

    const radius = Math.min(location.accuracyMeters ?? 0, 500);
    if (!circle.current) circle.current = L.circle(here, { radius, color: "#f2994a", weight: 1, fillOpacity: 0.12 }).addTo(m);
    else circle.current.setLatLng(here).setRadius(radius);

    if (!marker.current) marker.current = L.marker(here, { icon: riderIcon, zIndexOffset: 1000 }).addTo(m);
    else marker.current.setLatLng(here);

    if (followingRef.current) m.panTo(here, { animate: true, duration: 0.8 });
  }, [location, trail]);

  function recenter() {
    setFollowing(true);
    const m = map.current;
    if (!m) return;
    const pts = trail.length > 1 ? trail : [location];
    const bounds = L.latLngBounds(pts.map((p) => [p.latitude, p.longitude] as [number, number]));
    if (pts.length > 1) m.fitBounds(bounds.pad(0.3), { maxZoom: 17 });
    else m.setView([location.latitude, location.longitude], 16);
  }

  return (
    <div className="live-map">
      <div ref={el} className="live-map-canvas" />
      <div className="live-map-tools">
        <button className="secondary small" onClick={recenter} title="Show the whole route and follow the rider">
          <Icon name="target" size={16} /> {following ? "Show route" : "Recenter"}
        </button>
      </div>
    </div>
  );
}
