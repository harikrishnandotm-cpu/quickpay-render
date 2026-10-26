import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./main.jsx";

const profile = {
  id: 7,
  name: "Test Customer",
  email: "test@example.test",
  balance: 1000
};

const merchants = [{ id: 3, name: "Northstar Coffee", category: "Food & Drink" }];

function jsonResponse(data, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => data
  };
}

function authenticatedFetch(paymentResponse = {
  paymentId: "payment-123",
  status: "COMPLETED",
  merchantName: merchants[0].name,
  amount: 12.34,
  message: "Demo payment completed."
}) {
  return vi.fn(async (url) => {
    if (url === "/api/customers/me") return jsonResponse(profile);
    if (url === "/api/merchants") return jsonResponse(merchants);
    if (url === "/api/transactions") return jsonResponse([]);
    if (url === "/api/notifications") return jsonResponse([]);
    if (url === "/api/payments") return jsonResponse(paymentResponse);
    throw new Error(`Unexpected request: ${url}`);
  });
}

describe("Quickpay customer UI", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("shows the sign-in screen when there is no saved session", () => {
    render(<App />);

    expect(screen.getByRole("heading", { name: "Sign in to Quickpay" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /sign in/i })).toBeInTheDocument();
  });

  it("shows the authenticated customer overview", async () => {
    localStorage.setItem("quickpay-session", JSON.stringify({
      token: "test-token",
      customer: profile
    }));
    vi.stubGlobal("fetch", authenticatedFetch());

    render(<App />);

    expect(await screen.findByText("$1,000.00")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /Good to see you, Test/ })).toBeInTheDocument();
  });

  it("submits a payment and shows the confirmation", async () => {
    localStorage.setItem("quickpay-session", JSON.stringify({
      token: "test-token",
      customer: profile
    }));
    const fetchMock = authenticatedFetch();
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    await screen.findByText("$1,000.00");
    fireEvent.click(screen.getByRole("button", { name: "Start a payment" }));
    fireEvent.change(screen.getByLabelText("Choose a merchant"), { target: { value: "3" } });
    fireEvent.change(screen.getByLabelText(/Payment amount/), { target: { value: "12.34" } });
    fireEvent.click(screen.getByRole("button", { name: /review & pay/i }));

    expect(await screen.findByText("Demo payment completed.")).toBeInTheDocument();
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith("/api/payments", expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ merchantId: 3, amount: 12.34 })
      }));
    });
  });
});
