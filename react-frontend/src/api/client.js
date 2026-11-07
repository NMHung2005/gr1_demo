import keycloak from "../auth/keycloak";

export async function apiClient(path, options = {}) {
    if (!path.startsWith("/api/")) {
        throw new Error(
            "API client chỉ chấp nhận đường dẫn bắt đầu bằng /api/",
        );
    }

    if (!keycloak.authenticated) {
        throw new Error("Bạn cần đăng nhập.");
    }

    try {
        await keycloak.updateToken(30);
    } catch {
        keycloak.clearToken();
        throw new Error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    }

    if (!keycloak.token) {
        throw new Error("Không có access token.");
    }

    const headers = new Headers(options.headers);

    if (options.body && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }

    headers.set("Authorization", `Bearer ${keycloak.token}`);

    const response = await fetch(path, {
        ...options,
        headers,
    });

    const text = await response.text();
    const contentType = response.headers.get("content-type") || "";

    const data =
        text && contentType.includes("application/json")
            ? JSON.parse(text)
            : null;

    if (response.status === 401) {
        throw new Error(
            "Backend không chấp nhận token. Kiểm tra response và log backend.",
        );
    }

    if (response.status === 403) {
        throw new Error(
            "Bạn không có quyền hoặc API chưa được mở trong giai đoạn chuyển đổi.",
        );
    }

    if (!response.ok) {
        throw new Error(
            data?.message || data?.error || `HTTP ${response.status}`,
        );
    }

    return data;
}
