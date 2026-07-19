import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import CatalogWorkspace from "./CatalogWorkspace";
import { catalogApi, loadCatalog } from "../lib/catalog-api";

vi.mock("../lib/catalog-api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/catalog-api")>();
  return {
    ...actual,
    loadCatalog: vi.fn(),
    catalogApi: {
      ...actual.catalogApi,
      saveTeam: vi.fn(),
    },
  };
});

const page = <T,>(items: T[]) => ({ items, page: 0, size: 100, totalItems: items.length, totalPages: items.length ? 1 : 0 });
const catalog = {
  teams: page([{ id: "team-1", name: "Payments", contactChannel: "#payments", archivedAt: null, version: 0 }]),
  services: page([]),
  dependencies: page([]),
  runbooks: page([]),
  scenarios: page([]),
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("catalog workspace", () => {
  it("keeps the token in component state and submits generated-id resources through the protected API", async () => {
    vi.mocked(loadCatalog).mockResolvedValue(catalog);
    vi.mocked(catalogApi.saveTeam).mockResolvedValue({
      id: "generated-team-id", name: "Edge Reliability", contactChannel: "#edge", archivedAt: null, version: 0,
    });

    render(<CatalogWorkspace />);
    fireEvent.change(screen.getByLabelText("Short-lived ADMIN JWT"), { target: { value: "signed-admin-token" } });
    fireEvent.click(screen.getByRole("button", { name: "Connect" }));

    expect(await screen.findByText(/Connected\. Changes are persisted/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Teams" }));
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Edge Reliability" } });
    fireEvent.change(screen.getByLabelText("Contact channel"), { target: { value: "#edge" } });
    fireEvent.click(screen.getByRole("button", { name: "Save to PostgreSQL" }));

    await waitFor(() => expect(catalogApi.saveTeam).toHaveBeenCalledWith(
      "signed-admin-token",
      { name: "Edge Reliability", contactChannel: "#edge", version: 0 },
      undefined,
    ));
    expect(loadCatalog).toHaveBeenCalledWith("signed-admin-token");
  });
});
